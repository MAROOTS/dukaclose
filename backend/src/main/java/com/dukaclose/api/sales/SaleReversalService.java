// SaleReversalService.java
package com.dukaclose.api.sales;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import com.dukaclose.api.deni.DebtService;
import com.dukaclose.api.inventory.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaleReversalService {

    private final AccessService access;
    private final SaleRepository sales;
    private final SaleLineRepository saleLines;
    private final SaleReversalRepository reversals;
    private final ShopDayRepository days;
    private final ShopInventoryRepository inventory;
    private final StockMovementRepository movements;
    private final SaleService saleService;
    private final DebtService debtService;

    @Transactional
    public SaleResponse reverse(UUID userId, UUID shopId, UUID saleId, String rawReason) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);

        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED",
                    "Say why the sale is being reversed (at least 3 characters).");
        }

        // Same lock order as recording a sale: the day first, then the rows that change.
        // Each of these is the first load of that row in this transaction, so each reads fresh data.
        ShopDay openDay = days.findLockedForShare(shopId, DayStatus.OPEN).orElse(null);
        Sale sale = sales.findForUpdate(saleId, shopId)
                .orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "No such sale"));

        if (ctx.role() != Role.OWNER && !sale.getCashierId().equals(userId)) {
            throw ApiException.forbidden("NOT_YOUR_SALE",
                    "You can only reverse sales you recorded yourself. Ask the owner.");
        }
        if (openDay == null || !openDay.getId().equals(sale.getShopDayId())) {
            throw ApiException.conflict("DAY_CLOSED",
                    "This sale belongs to a day that is already closed. Ask the owner to record an adjustment.");
        }
        if (sale.getStatus() == SaleStatus.REVERSED) {
            throw ApiException.conflict("ALREADY_REVERSED", "This sale was already reversed.");
        }

        List<SaleLine> lines = saleLines.findBySaleIdOrderByLineNo(saleId);
        Map<UUID, ShopInventory> stock = new HashMap<>();
        lines.stream().map(SaleLine::getItemId).sorted().forEach(itemId ->
                inventory.findForUpdate(shopId, itemId).ifPresent(row -> stock.put(itemId, row)));

        for (SaleLine line : lines) {
            ShopInventory row = stock.get(line.getItemId());
            if (row == null) {
                throw new IllegalStateException("Stock record missing for sold item " + line.getItemId());
            }
            // The units come back at the cost they left at, blended into the current average
            BigDecimal newOnHand = row.getOnHand().add(line.getQuantity());
            BigDecimal newAverage = row.getOnHand().multiply(row.getAverageCost())
                    .add(line.getQuantity().multiply(line.getUnitCost()))
                    .divide(newOnHand, 4, RoundingMode.HALF_UP);
            row.applyStockIn(newOnHand, newAverage);

            movements.save(StockMovement.forSale(sale.getBusinessId(), shopId, openDay.getId(), line.getItemId(),
                    MovementType.SALE_REVERSAL, line.getQuantity(), newOnHand, line.getUnitCost(),
                    saleId, userId));
        }

        reversals.save(new SaleReversal(saleId, shopId, sale.getBusinessId(), openDay.getId(), reason, userId));
        sale.markReversed();
        debtService.voidForSale(saleId);
        sales.flush();
        return saleService.one(sale);
    }
}