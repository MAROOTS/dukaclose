package com.dukaclose.api.inventory;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.RecordStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999");

    private final AccessService access;
    private final ItemRepository items;
    private final ShopInventoryRepository inventory;
    private final StockMovementRepository movements;
    private final UserRepository users;

    record Mismatch(UUID itemId, BigDecimal onHand, BigDecimal ledgerSum) {}

    // ------------------------------------------------------------------ stock in

    @Transactional
    public MovementResponse openingStock(UUID userId, UUID shopId, UUID itemId, StockInRequest request) {
        return stockIn(MovementType.OPENING, userId, shopId, itemId, request);
    }

    @Transactional
    public MovementResponse receive(UUID userId, UUID shopId, UUID itemId, StockInRequest request) {
        return stockIn(MovementType.RECEIPT, userId, shopId, itemId, request);
    }

    private MovementResponse stockIn(MovementType type, UUID userId, UUID shopId, UUID itemId, StockInRequest req) {
        Shop shop = access.requireShopOwner(userId, shopId).shop();

        Optional<StockMovement> replay = movements.findByShopIdAndRequestId(shopId, req.requestId());
        if (replay.isPresent()) {
            return replayOf(replay.get(), type, itemId);
        }

        Item item = activeItem(shop, itemId);
        BigDecimal quantity = resolveQuantity(item, req.quantity(), req.packs());

        if (type == MovementType.OPENING && movements.existsByShopIdAndItemId(shopId, itemId)) {
            throw ApiException.conflict("OPENING_ALREADY_SET",
                    "Opening stock was already recorded for this item. Use a receipt or an adjustment instead.");
        }

        ShopInventory row = lockOrCreate(shop, itemId);

        BigDecimal paid = BigDecimal.valueOf(req.totalCostMinor());
        BigDecimal newOnHand = row.getOnHand().add(quantity);
        BigDecimal newAverage = row.getOnHand().multiply(row.getAverageCost()).add(paid)
                .divide(newOnHand, 4, RoundingMode.HALF_UP);
        BigDecimal unitCost = paid.divide(quantity, 4, RoundingMode.HALF_UP);

        row.applyStockIn(newOnHand, newAverage);
        StockMovement movement = movements.saveAndFlush(new StockMovement(
                shop.getBusinessId(), shopId, itemId, req.requestId(), type, quantity, newOnHand,
                unitCost, req.totalCostMinor(), null, cleanNote(req.note()), userId));
        return MovementResponse.of(movement, nameOf(userId));
    }

    // ----------------------------------------------------------------- adjustments

    @Transactional
    public MovementResponse adjust(UUID userId, UUID shopId, UUID itemId, AdjustStockRequest req) {
        Shop shop = access.requireShopOwner(userId, shopId).shop();

        Optional<StockMovement> replay = movements.findByShopIdAndRequestId(shopId, req.requestId());
        if (replay.isPresent()) {
            return replayOf(replay.get(), MovementType.ADJUSTMENT, itemId);
        }

        Item item = activeItem(shop, itemId);
        BigDecimal delta = req.quantityDelta();
        if (delta.signum() == 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "An adjustment cannot be zero");
        }
        checkReasonMatchesDirection(req.reason(), delta);
        if (delta.abs().compareTo(MAX_QUANTITY) > 0 || !item.getUnit().fitsScale(delta)) {
            throw ApiException.badRequest("INVALID_QUANTITY", "That quantity is not valid for " + item.getUnit());
        }
        delta = delta.setScale(3, RoundingMode.UNNECESSARY);

        ShopInventory row = delta.signum() > 0
                ? lockOrCreate(shop, itemId)
                : inventory.findForUpdate(shopId, itemId)
                .orElseThrow(() -> insufficient(BigDecimal.ZERO));

        BigDecimal newOnHand = row.getOnHand().add(delta);
        if (newOnHand.signum() < 0) {
            throw insufficient(row.getOnHand());
        }

        BigDecimal costAtTheTime = row.getAverageCost();     // stock leaves (or is found) at the current average
        row.applyAdjustment(newOnHand);
        StockMovement movement = movements.saveAndFlush(new StockMovement(
                shop.getBusinessId(), shopId, itemId, req.requestId(), MovementType.ADJUSTMENT, delta, newOnHand,
                costAtTheTime, null, req.reason(), cleanNote(req.note()), userId));
        return MovementResponse.of(movement, nameOf(userId));
    }

    // --------------------------------------------------------------------- history

    @Transactional(readOnly = true)
    public List<MovementResponse> history(UUID userId, UUID shopId, UUID itemId, int limit) {
        Shop shop = access.requireShopOwner(userId, shopId).shop();
        items.findByIdAndBusinessId(itemId, shop.getBusinessId())      // archived items keep their history
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "No such item in this business"));

        List<StockMovement> rows = movements.findByShopIdAndItemIdOrderBySeqDesc(
                shopId, itemId, PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));

        Map<UUID, String> names = users.findAllById(rows.stream().map(StockMovement::getCreatedBy).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        return rows.stream().map(m -> MovementResponse.of(m, names.getOrDefault(m.getCreatedBy(), ""))).toList();
    }

    // -------------------------------------------------------------- reconciliation

    /**
     * Compares every on_hand balance with the sum of its ledger. An empty list means everything reconciles.
     * Package-private on purpose: it does no access check, so it is never exposed through the API.
     */
    List<Mismatch> findMismatches(UUID shopId) {
        Map<UUID, BigDecimal> ledger = new HashMap<>();
        for (Object[] row : movements.sumByItem(shopId)) {
            ledger.put((UUID) row[0], (BigDecimal) row[1]);
        }
        List<Mismatch> bad = new ArrayList<>();
        for (ShopInventory row : inventory.findByShopId(shopId)) {
            BigDecimal sum = ledger.getOrDefault(row.getItemId(), BigDecimal.ZERO);
            if (row.getOnHand().compareTo(sum) != 0) {
                bad.add(new Mismatch(row.getItemId(), row.getOnHand(), sum));
            }
        }
        return bad;
    }

    // --------------------------------------------------------------------- helpers

    private Item activeItem(Shop shop, UUID itemId) {
        return items.findByIdAndBusinessId(itemId, shop.getBusinessId())
                .filter(i -> i.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "No such item in this business"));
    }

    private ShopInventory lockOrCreate(Shop shop, UUID itemId) {
        return inventory.findForUpdate(shop.getId(), itemId)
                .orElseGet(() -> inventory.saveAndFlush(
                        new ShopInventory(shop.getId(), shop.getBusinessId(), itemId, null)));
    }

    private BigDecimal resolveQuantity(Item item, BigDecimal quantity, BigDecimal packs) {
        if ((quantity == null) == (packs == null)) {
            throw ApiException.badRequest("INVALID_QUANTITY", "Enter either a quantity or a number of packs, not both");
        }
        BigDecimal result;
        if (packs != null) {
            if (item.getPackSize() == null) {
                throw ApiException.badRequest("NO_PACK", "This item has no pack size. Enter a quantity instead.");
            }
            result = packs.multiply(item.getPackSize());
        } else {
            result = quantity;
        }
        if (result.signum() <= 0 || result.compareTo(MAX_QUANTITY) > 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "That quantity is not valid");
        }
        if (!item.getUnit().fitsScale(result)) {
            throw ApiException.badRequest("INVALID_QUANTITY", "That quantity has too many decimals for " + item.getUnit());
        }
        return result.setScale(3, RoundingMode.UNNECESSARY);
    }

    private void checkReasonMatchesDirection(AdjustmentReason reason, BigDecimal delta) {
        boolean mustRemove = reason == AdjustmentReason.DAMAGED || reason == AdjustmentReason.EXPIRED
                || reason == AdjustmentReason.LOST;
        if (mustRemove && delta.signum() > 0) {
            throw ApiException.badRequest("INVALID_REASON", reason + " must remove stock, not add it");
        }
        if (reason == AdjustmentReason.FOUND && delta.signum() < 0) {
            throw ApiException.badRequest("INVALID_REASON", "FOUND must add stock, not remove it");
        }
    }

    private MovementResponse replayOf(StockMovement existing, MovementType type, UUID itemId) {
        if (!existing.getItemId().equals(itemId) || existing.getMovementType() != type) {
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "That request id was already used for something else");
        }
        return MovementResponse.of(existing, nameOf(existing.getCreatedBy()));
    }

    private ApiException insufficient(BigDecimal onHand) {
        return ApiException.conflict("INSUFFICIENT_STOCK",
                "Only " + onHand.stripTrailingZeros().toPlainString() + " on hand");
    }

    private String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(User::getDisplayName).orElse("");
    }
}