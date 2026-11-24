// RefundService.java
package com.dukaclose.api.refunds;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.Hashing;
import com.dukaclose.api.common.Money;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import com.dukaclose.api.deni.CustomerDebt;
import com.dukaclose.api.deni.DebtService;
import com.dukaclose.api.deni.DebtStatus;
import com.dukaclose.api.inventory.*;
import com.dukaclose.api.sales.Sale;
import com.dukaclose.api.sales.SaleLine;
import com.dukaclose.api.sales.SaleLineRepository;
import com.dukaclose.api.sales.SaleRepository;
import com.dukaclose.api.sales.SaleStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final AccessService access;
    private final ShopDayRepository days;
    private final SaleRepository sales;
    private final SaleLineRepository saleLines;
    private final ItemRepository items;
    private final ShopInventoryRepository inventory;
    private final StockMovementRepository movements;
    private final SaleRefundRepository refunds;
    private final SaleRefundLineRepository refundLines;
    private final SaleRefundPayoutRepository payoutRepo;
    private final DebtService debtService;
    private final UserRepository users;

    public record Result(RefundResponse refund, boolean replayed) {}

    private record LineCalc(SaleLine line, Item item, BigDecimal quantity, Disposition disposition, long amount) {}

    private record Calc(List<LineCalc> lines, long total, CustomerDebt debt, long debtCredit) {
        long payout() { return total - debtCredit; }
    }

    // ------------------------------------------------------------------ record a refund

    @Transactional
    public Result create(UUID userId, UUID shopId, UUID saleId, UUID key, RefundRequest request) {
        Shop shop = access.requireShopOwner(userId, shopId).shop();     // only the owner refunds money
        String reason = request.reason().trim();
        if (reason.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED", "Say why this refund is being made (at least 3 characters).");
        }
        String hash = fingerprint(saleId, request, reason);

        Optional<Result> replay = replayOf(shopId, key, hash);
        if (replay.isPresent()) return replay.get();

        // Lock order is the same as everywhere else: the day, then the sale, then the debt, then the stock rows
        ShopDay today = days.findLockedForShare(shopId, DayStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("NO_OPEN_DAY",
                        "Open today’s book first. A refund is recorded on today’s book."));
        Sale sale = sales.findForUpdate(saleId, shopId)
                .orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "No such sale"));

        replay = replayOf(shopId, key, hash);          // an identical request that was waiting on the lock
        if (replay.isPresent()) return replay.get();

        requireRefundable(sale);

        CustomerDebt debt = debtService.lockForSale(saleId).orElse(null);
        Calc calc = calculate(sale, request.lines(), debt);

        // How the money went back must match what the customer is actually owed
        Set<PayoutMethod> seen = EnumSet.noneOf(PayoutMethod.class);
        long paidBack = 0;
        for (RefundRequest.PayoutRequest p : request.payouts()) {
            if (!seen.add(p.method())) {
                throw ApiException.badRequest("INVALID_PAYOUT", "Each payment method can be used only once.");
            }
            paidBack += p.amountMinor();
        }
        if (paidBack != calc.payout()) {
            throw ApiException.badRequest("PAYOUT_MISMATCH", calc.payout() == 0
                    ? "This refund is covered by what the customer still owes, so nothing is paid back."
                    : "Pay back " + Money.ksh(calc.payout()) + " in total. The payments add up to "
                    + Money.ksh(paidBack) + ".");
        }

        // Goods going back on the shelf: lock their stock rows in a fixed order
        Map<UUID, ShopInventory> stock = new HashMap<>();
        calc.lines().stream().filter(l -> l.disposition() == Disposition.RESTOCK)
                .map(l -> l.line().getItemId()).distinct().sorted()
                .forEach(id -> inventory.findForUpdate(shopId, id).ifPresent(row -> stock.put(id, row)));

        // ---- write everything, in this one transaction ----
        SaleRefund refund = refunds.saveAndFlush(new SaleRefund(shop.getBusinessId(), shopId, today.getId(), saleId,
                sale.getShopDayId(), calc.total(), calc.debtCredit(), reason, userId, key, hash));

        for (LineCalc l : calc.lines()) {
            refundLines.save(new SaleRefundLine(refund.getId(), shopId, saleId, l.line().getId(),
                    l.line().getItemId(), l.quantity(), l.line().getUnitPriceMinor(), l.line().getUnitCost(),
                    l.amount(), l.disposition()));

            if (l.disposition() == Disposition.RESTOCK) {
                ShopInventory row = stock.get(l.line().getItemId());
                if (row == null) {
                    throw new IllegalStateException("Stock record missing for sold item " + l.line().getItemId());
                }
                // the units come back at the cost they left at, blended into the current average
                BigDecimal newOnHand = row.getOnHand().add(l.quantity());
                BigDecimal newAverage = row.getOnHand().multiply(row.getAverageCost())
                        .add(l.quantity().multiply(l.line().getUnitCost()))
                        .divide(newOnHand, 4, RoundingMode.HALF_UP);
                row.applyStockIn(newOnHand, newAverage);
                movements.save(StockMovement.forRefund(shop.getBusinessId(), shopId, today.getId(),
                        l.line().getItemId(), l.quantity(), newOnHand, l.line().getUnitCost(), saleId,
                        refund.getId(), userId));
            }
        }
        for (RefundRequest.PayoutRequest p : request.payouts()) {
            payoutRepo.save(new SaleRefundPayout(refund.getId(), shopId, p.method(), p.amountMinor()));
        }
        if (calc.debtCredit() > 0) {
            debtService.credit(shop.getBusinessId(), calc.debt(), refund.getId(), calc.debtCredit());
        }
        refunds.flush();

        return new Result(one(refund), false);
    }

    // -------------------------------------------------------------- quote and options

    /** Works out a refund without saving anything, so the screen shows the server's numbers. */
    @Transactional(readOnly = true)
    public RefundQuote quote(UUID userId, UUID shopId, UUID saleId, List<RefundRequest.LineRequest> lines) {
        access.requireShopOwner(userId, shopId);
        Sale sale = sales.findByIdAndShopId(saleId, shopId)
                .orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "No such sale"));
        requireRefundable(sale);
        CustomerDebt debt = debtService.forSales(List.of(saleId)).get(saleId);
        Calc calc = calculate(sale, lines, debt);
        return new RefundQuote(calc.total(), calc.debtCredit(), calc.payout(),
                calc.lines().stream().map(l -> new RefundQuote.Line(l.line().getId(), l.amount())).toList());
    }

    @Transactional(readOnly = true)
    public RefundOptions options(UUID userId, UUID shopId, UUID saleId) {
        access.requireShopAccess(userId, shopId);
        Sale sale = sales.findByIdAndShopId(saleId, shopId)
                .orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "No such sale"));
        ShopDay saleDay = days.findById(sale.getShopDayId()).orElseThrow();

        String blocked = sale.getStatus() == SaleStatus.REVERSED ? "REVERSED"
                : saleDay.getStatus() != DayStatus.CLOSED ? "SALE_DAY_OPEN" : null;

        List<SaleLine> lines = saleLines.findBySaleIdOrderByLineNo(saleId);
        Map<UUID, Item> itemMap = items.findAllById(lines.stream().map(SaleLine::getItemId).toList()).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<UUID, BigDecimal> refundedQty = new HashMap<>();
        for (Object[] row : refundLines.sumsBySaleLine(saleId)) {
            refundedQty.put((UUID) row[0], (BigDecimal) row[1]);
        }

        List<RefundOptions.Line> optionLines = lines.stream().map(l -> {
            BigDecimal done = refundedQty.getOrDefault(l.getId(), BigDecimal.ZERO);
            Item item = itemMap.get(l.getItemId());
            return new RefundOptions.Line(l.getId(), l.getItemId(), item.getName(), item.getUnit(),
                    l.getQuantity(), l.getUnitPriceMinor(), done, l.getQuantity().subtract(done));
        }).toList();

        CustomerDebt debt = debtService.forSales(List.of(saleId)).get(saleId);
        long owed = debt == null || debt.getStatus() != DebtStatus.ACTIVE ? 0
                : debtService.balances(List.of(debt)).get(debt.getId()).outstandingMinor();

        return new RefundOptions(saleId, sale.getReceiptNo(), blocked == null, blocked, sale.getTotalMinor(), owed,
                optionLines, responses(refunds.findBySaleIdOrderByCreatedAtDesc(saleId)));
    }

    // ------------------------------------------------------------------- the arithmetic

    private void requireRefundable(Sale sale) {
        if (sale.getStatus() == SaleStatus.REVERSED) {
            throw ApiException.conflict("SALE_REVERSED", "This sale was reversed, so there is nothing to refund.");
        }
        ShopDay saleDay = days.findById(sale.getShopDayId()).orElseThrow();
        if (saleDay.getStatus() != DayStatus.CLOSED) {
            throw ApiException.conflict("SALE_DAY_OPEN",
                    "This sale is from a day that is still open. Reverse it instead of refunding it.");
        }
    }

    /** Prices each returned line from the original sale, caps quantities, and splits the deni credit from the payout. */
    private Calc calculate(Sale sale, List<RefundRequest.LineRequest> requested, CustomerDebt debt) {
        Map<UUID, SaleLine> sold = saleLines.findBySaleIdOrderByLineNo(sale.getId()).stream()
                .collect(Collectors.toMap(SaleLine::getId, Function.identity()));
        Map<UUID, Item> itemMap = items.findAllById(sold.values().stream().map(SaleLine::getItemId).toList()).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<UUID, BigDecimal> refundedQty = new HashMap<>();
        Map<UUID, Long> refundedAmount = new HashMap<>();
        for (Object[] row : refundLines.sumsBySaleLine(sale.getId())) {
            refundedQty.put((UUID) row[0], (BigDecimal) row[1]);
            refundedAmount.put((UUID) row[0], ((Number) row[2]).longValue());
        }

        Set<UUID> seen = new HashSet<>();
        List<LineCalc> result = new ArrayList<>();
        long total = 0;
        for (RefundRequest.LineRequest r : requested) {
            if (!seen.add(r.saleLineId())) {
                throw ApiException.badRequest("INVALID_LINES", "The same item appears twice. Combine it into one line.");
            }
            SaleLine line = sold.get(r.saleLineId());
            if (line == null) {
                throw ApiException.notFound("LINE_NOT_FOUND", "One of the items is not part of this sale.");
            }
            Item item = itemMap.get(line.getItemId());
            BigDecimal qty = r.quantity();
            if (!item.getUnit().fitsScale(qty)) {
                throw ApiException.badRequest("INVALID_QUANTITY",
                        item.getName() + ": that quantity is not valid for " + unitWord(item.getUnit()) + ".");
            }
            qty = qty.setScale(3, RoundingMode.UNNECESSARY);

            BigDecimal remaining = line.getQuantity().subtract(refundedQty.getOrDefault(line.getId(), BigDecimal.ZERO));
            if (qty.compareTo(remaining) > 0) {
                throw ApiException.conflict("REFUND_EXCEEDS_SOLD", item.getName() + ": only "
                        + remaining.stripTrailingZeros().toPlainString() + " " + unitWord(item.getUnit())
                        + " can still be refunded.");
            }
            // Refunding what is left of a line returns exactly what is left of its money, so cents never drift
            long amount = qty.compareTo(remaining) == 0
                    ? line.getLineTotalMinor() - refundedAmount.getOrDefault(line.getId(), 0L)
                    : qty.multiply(BigDecimal.valueOf(line.getUnitPriceMinor()))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            total += amount;
            result.add(new LineCalc(line, item, qty, r.disposition(), amount));
        }
        if (total <= 0) {
            throw ApiException.badRequest("REFUND_TOTAL_ZERO", "That refund is worth nothing. Check the items and quantities.");
        }

        // The customer should not pay for goods they returned: what they still owe on this sale goes first
        long credit = 0;
        if (debt != null && debt.getStatus() == DebtStatus.ACTIVE) {
            long outstanding = debtService.balances(List.of(debt)).get(debt.getId()).outstandingMinor();
            credit = Math.max(0, Math.min(total, outstanding));
        }
        return new Calc(result, total, debt, credit);
    }

    // ---------------------------------------------------------------------- responses

    private Optional<Result> replayOf(UUID shopId, UUID key, String hash) {
        return refunds.findByShopIdAndIdempotencyKey(shopId, key).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
                        "That Idempotency-Key was already used for a different refund.");
            }
            return new Result(one(existing), true);
        });
    }

    RefundResponse one(SaleRefund refund) {
        return responses(List.of(refund)).get(0);
    }

    List<RefundResponse> responses(List<SaleRefund> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(SaleRefund::getId).toList();

        Map<UUID, List<SaleRefundLine>> linesByRefund = refundLines.findByRefundIdIn(ids).stream()
                .collect(Collectors.groupingBy(SaleRefundLine::getRefundId));
        Map<UUID, List<SaleRefundPayout>> payoutsByRefund = payoutRepo.findByRefundIdIn(ids).stream()
                .collect(Collectors.groupingBy(SaleRefundPayout::getRefundId));
        Map<UUID, Item> itemMap = items.findAllById(linesByRefund.values().stream().flatMap(List::stream)
                        .map(SaleRefundLine::getItemId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<UUID, Sale> saleMap = sales.findAllById(list.stream().map(SaleRefund::getSaleId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Sale::getId, Function.identity()));
        Set<UUID> dayIds = new HashSet<>();
        list.forEach(r -> {
            dayIds.add(r.getShopDayId());
            dayIds.add(r.getSaleDayId());
        });
        Map<UUID, LocalDate> dates = days.findAllById(dayIds).stream()
                .collect(Collectors.toMap(ShopDay::getId, ShopDay::getBusinessDate));
        Map<UUID, String> names = users.findAllById(
                        list.stream().map(SaleRefund::getRefundedBy).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        return list.stream().map(r -> new RefundResponse(r.getId(), r.getSaleId(),
                saleMap.get(r.getSaleId()).getReceiptNo(), r.getShopId(), dates.get(r.getShopDayId()),
                dates.get(r.getSaleDayId()), r.getTotalMinor(), r.getDebtCreditMinor(), r.getReason(),
                names.getOrDefault(r.getRefundedBy(), ""), r.getCreatedAt(),
                linesByRefund.getOrDefault(r.getId(), List.of()).stream().map(l -> {
                    Item item = itemMap.get(l.getItemId());
                    return new RefundResponse.Line(l.getSaleLineId(), l.getItemId(), item.getName(), item.getUnit(),
                            l.getQuantity(), l.getUnitPriceMinor(), l.getLineRefundMinor(), l.getDisposition());
                }).toList(),
                payoutsByRefund.getOrDefault(r.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(SaleRefundPayout::getMethod))
                        .map(p -> new RefundResponse.Payout(p.getMethod(), p.getAmountMinor())).toList()
        )).toList();
    }

    private static String fingerprint(UUID saleId, RefundRequest request, String reason) {
        String lines = request.lines().stream()
                .sorted(Comparator.comparing(RefundRequest.LineRequest::saleLineId))
                .map(l -> l.saleLineId() + ":" + l.quantity().stripTrailingZeros().toPlainString() + ":" + l.disposition())
                .collect(Collectors.joining(","));
        String pays = request.payouts().stream()
                .sorted(Comparator.comparing(p -> p.method().name()))
                .map(p -> p.method() + ":" + p.amountMinor())
                .collect(Collectors.joining(","));
        return Hashing.sha256Hex(saleId + "|" + lines + "|" + pays + "|" + reason);
    }

    private static String unitWord(Unit unit) {
        return switch (unit) {
            case PIECE -> "pc";
            case KG -> "kg";
            case LITRE -> "L";
        };
    }
}