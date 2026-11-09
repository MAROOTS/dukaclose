// SaleService.java
package com.dukaclose.api.sales;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.RecordStatus;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import com.dukaclose.api.inventory.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaleService {

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999");
    private static final long MAX_TOTAL_MINOR = 10_000_000_000L;      // KSh 100 million

    private final AccessService access;
    private final ShopDayRepository days;
    private final ItemRepository items;
    private final ShopInventoryRepository inventory;
    private final StockMovementRepository movements;
    private final SaleRepository sales;
    private final SaleLineRepository saleLines;
    private final SaleAllocationRepository allocations;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final SaleReversalRepository reversalRepo;
    private final BusinessClock clock;

    public record SaleResult(SaleResponse sale, boolean replayed) {}

    /** A line that passed every check, with everything needed to write it. */
    private record Priced(Item item, ShopInventory stock, BigDecimal quantity, long unitPrice,
                          BigDecimal unitCost, long lineTotal, long lineCost, BigDecimal newOnHand) {}

    // ------------------------------------------------------------------ create

    @Transactional
    public SaleResult create(UUID userId, UUID shopId, UUID key, CreateSaleRequest request) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        String hash = fingerprint(request);

        // A retry of a sale we already saved gets the original back, even if the day has closed since
        Optional<SaleResult> replay = replayOf(shopId, key, hash);
        if (replay.isPresent()) return replay.get();

        // Shared lock: a day that is closing waits for sales in flight, and sales wait for the close
        ShopDay day = days.findLockedForShare(shopId, DayStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("NO_OPEN_DAY", "Open today’s book before recording sales."));

        checkShape(request);

        Map<UUID, Item> catalogue = items.findAllById(
                        request.lines().stream().map(CreateSaleRequest.LineRequest::itemId).toList()).stream()
                .filter(i -> i.getBusinessId().equals(shop.getBusinessId()) && i.getStatus() == RecordStatus.ACTIVE)
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        // Lock stock rows in a fixed order, so two sales can never deadlock each other
        Map<UUID, ShopInventory> stock = new HashMap<>();
        catalogue.keySet().stream().sorted().forEach(itemId ->
                inventory.findForUpdate(shopId, itemId).ifPresent(row -> stock.put(itemId, row)));

        // An identical request that was waiting on those locks may have just been committed
        replay = replayOf(shopId, key, hash);
        if (replay.isPresent()) return replay.get();

        // ---- validate everything, in the order the cashier entered it. Nothing is written yet. ----
        List<Priced> priced = new ArrayList<>();
        long total = 0;
        for (CreateSaleRequest.LineRequest line : request.lines()) {
            Item item = catalogue.get(line.itemId());
            if (item == null) {
                throw ApiException.notFound("ITEM_NOT_FOUND", "One of the items is not available in this shop.");
            }
            BigDecimal quantity = line.quantity();
            if (quantity.compareTo(MAX_QUANTITY) > 0 || !item.getUnit().fitsScale(quantity)) {
                throw ApiException.badRequest("INVALID_QUANTITY",
                        item.getName() + ": that quantity is not valid for " + unitWord(item.getUnit()) + ".");
            }
            quantity = quantity.setScale(3, RoundingMode.UNNECESSARY);

            ShopInventory row = stock.get(item.getId());
            if (row == null || row.getPriceMinor() == null) {
                throw ApiException.conflict("NO_PRICE",
                        item.getName() + " has no price in this shop yet. Ask the owner to set it.");
            }
            if (row.getOnHand().compareTo(quantity) < 0) {
                throw ApiException.conflict("INSUFFICIENT_STOCK", item.getName() + ": only "
                        + plain(row.getOnHand()) + " " + unitWord(item.getUnit()) + " on hand.");
            }

            long lineTotal = cents(quantity.multiply(BigDecimal.valueOf(row.getPriceMinor())));
            long lineCost = cents(quantity.multiply(row.getAverageCost()));
            if (lineTotal > MAX_TOTAL_MINOR) {
                throw ApiException.badRequest("SALE_TOO_LARGE", "That sale is too large to record.");
            }
            total += lineTotal;
            priced.add(new Priced(item, row, quantity, row.getPriceMinor(), row.getAverageCost(),
                    lineTotal, lineCost, row.getOnHand().subtract(quantity)));
        }
        if (total > MAX_TOTAL_MINOR) {
            throw ApiException.badRequest("SALE_TOO_LARGE", "That sale is too large to record.");
        }
        if (total == 0) {
            throw ApiException.badRequest("SALE_TOTAL_ZERO", "The sale total is zero. Check the item prices.");
        }
        long paid = request.allocations().stream().mapToLong(CreateSaleRequest.AllocationRequest::amountMinor).sum();
        if (paid != total) {
            throw ApiException.badRequest("ALLOCATION_MISMATCH", "Payments add up to KSh "
                    + BigDecimal.valueOf(paid, 2).toPlainString() + " but the sale total is KSh "
                    + BigDecimal.valueOf(total, 2).toPlainString() + ".");
        }

        // ---- write everything, in this one transaction ----
        Long receiptNo = jdbc.queryForObject(
                "UPDATE shop_counters SET last_receipt_no = last_receipt_no + 1 WHERE shop_id = ? RETURNING last_receipt_no",
                Long.class, shopId);
        if (receiptNo == null) throw new IllegalStateException("No receipt counter for shop " + shopId);

        Sale sale = sales.save(new Sale(shop.getBusinessId(), shopId, day.getId(), receiptNo, userId,
                total, key, hash));

        int lineNo = 0;
        for (Priced p : priced) {
            saleLines.save(new SaleLine(sale.getId(), shopId, shop.getBusinessId(), p.item().getId(), ++lineNo,
                    p.quantity(), p.unitPrice(), p.unitCost(), p.lineTotal(), p.lineCost()));
            p.stock().applySale(p.newOnHand());
            movements.save(StockMovement.forSale(shop.getBusinessId(), shopId, day.getId(), p.item().getId(),
                    MovementType.SALE, p.quantity().negate(), p.newOnHand(), p.unitCost(), sale.getId(), userId));
        }
        for (CreateSaleRequest.AllocationRequest a : request.allocations()) {
            allocations.save(new SaleAllocation(sale.getId(), shopId, a.method(), a.amountMinor()));
        }
        sales.flush();

        return new SaleResult(one(sale), false);
    }

    // --------------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public SaleResponse get(UUID userId, UUID shopId, UUID saleId) {
        access.requireShopAccess(userId, shopId);
        Sale sale = sales.findByIdAndShopId(saleId, shopId)
                .orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "No such sale"));
        return one(sale);
    }

    // ------------------------------------------------------------------- helpers

    private Optional<SaleResult> replayOf(UUID shopId, UUID key, String hash) {
        return sales.findByShopIdAndIdempotencyKey(shopId, key).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
                        "That Idempotency-Key was already used for a different sale.");
            }
            return new SaleResult(one(existing), true);
        });
    }

    private void checkShape(CreateSaleRequest request) {
        Set<UUID> seenItems = new HashSet<>();
        for (CreateSaleRequest.LineRequest line : request.lines()) {
            if (!seenItems.add(line.itemId())) {
                throw ApiException.badRequest("INVALID_LINES", "The same item appears twice. Combine it into one line.");
            }
        }
        Set<PaymentMethod> seenMethods = EnumSet.noneOf(PaymentMethod.class);
        for (CreateSaleRequest.AllocationRequest a : request.allocations()) {
            if (a.method() == PaymentMethod.DENI) {
                throw ApiException.badRequest("DENI_NOT_AVAILABLE", "Deni sales are not available yet.");
            }
            if (!seenMethods.add(a.method())) {
                throw ApiException.badRequest("INVALID_ALLOCATION", "Each payment method can be used only once.");
            }
        }
    }

    SaleResponse one(Sale sale) {
        return responses(List.of(sale)).get(0);
    }

    @Transactional(readOnly = true)
    public SalePage list(UUID userId, UUID shopId, LocalDate date, int page, int size) {
        access.requireShopAccess(userId, shopId);

        LocalDate target = date != null ? date
                : days.findByShopIdAndStatus(shopId, DayStatus.OPEN).map(ShopDay::getBusinessDate).orElse(clock.today());
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "receiptNo"));

        Page<Sale> result = days.findByShopIdAndBusinessDate(shopId, target)
                .map(day -> sales.findByShopDayId(day.getId(), pageable))
                .orElse(Page.empty(pageable));

        return new SalePage(target, responses(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** Builds responses for many sales with a handful of queries, however many sales there are. */
    List<SaleResponse> responses(List<Sale> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(Sale::getId).toList();

        Map<UUID, List<SaleLine>> linesBySale = saleLines.findBySaleIdIn(ids).stream()
                .collect(Collectors.groupingBy(SaleLine::getSaleId));
        Map<UUID, List<SaleAllocation>> paymentsBySale = allocations.findBySaleIdIn(ids).stream()
                .collect(Collectors.groupingBy(SaleAllocation::getSaleId));
        Map<UUID, SaleReversal> reversalBySale = reversalRepo.findBySaleIdIn(ids).stream()
                .collect(Collectors.toMap(SaleReversal::getSaleId, Function.identity()));

        Set<UUID> itemIds = linesBySale.values().stream().flatMap(List::stream)
                .map(SaleLine::getItemId).collect(Collectors.toSet());
        Map<UUID, Item> itemMap = items.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        Set<UUID> userIds = new HashSet<>();
        list.forEach(s -> userIds.add(s.getCashierId()));
        reversalBySale.values().forEach(r -> userIds.add(r.getReversedBy()));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        Map<UUID, LocalDate> dates = days.findAllById(
                        list.stream().map(Sale::getShopDayId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ShopDay::getId, ShopDay::getBusinessDate));

        return list.stream().map(sale -> {
            List<SaleResponse.Line> lineViews = linesBySale.getOrDefault(sale.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(SaleLine::getLineNo))
                    .map(l -> {
                        Item item = itemMap.get(l.getItemId());
                        return new SaleResponse.Line(l.getItemId(), item.getName(), item.getUnit(),
                                l.getQuantity(), l.getUnitPriceMinor(), l.getLineTotalMinor());
                    }).toList();
            List<SaleResponse.Allocation> payments = paymentsBySale.getOrDefault(sale.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(SaleAllocation::getMethod))
                    .map(a -> new SaleResponse.Allocation(a.getMethod(), a.getAmountMinor())).toList();
            SaleReversal reversal = reversalBySale.get(sale.getId());
            SaleResponse.Reversal reversalView = reversal == null ? null : new SaleResponse.Reversal(
                    reversal.getReason(), names.getOrDefault(reversal.getReversedBy(), ""), reversal.getCreatedAt());

            return new SaleResponse(sale.getId(), sale.getReceiptNo(), sale.getShopId(), dates.get(sale.getShopDayId()),
                    sale.getStatus(), sale.getTotalMinor(), names.getOrDefault(sale.getCashierId(), ""),
                    sale.getCreatedAt(), lineViews, payments, reversalView);
        }).toList();
    }

    /** Same sale, same fingerprint, however the lines and payments are ordered. */
    private static String fingerprint(CreateSaleRequest request) {
        String lines = request.lines().stream()
                .sorted(Comparator.comparing(CreateSaleRequest.LineRequest::itemId))
                .map(l -> l.itemId() + ":" + l.quantity().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(","));
        String payments = request.allocations().stream()
                .sorted(Comparator.comparing(a -> a.method().name()))
                .map(a -> a.method() + ":" + a.amountMinor())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((lines + "|" + payments).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long cents(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String unitWord(Unit unit) {
        return switch (unit) {
            case PIECE -> "pc";
            case KG -> "kg";
            case LITRE -> "L";
        };
    }
}