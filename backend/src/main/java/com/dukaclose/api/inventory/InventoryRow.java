package com.dukaclose.api.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of a shop's inventory. averageCostMinor is null for cashiers. */
public record InventoryRow(UUID itemId, String name, Unit unit, String packName, BigDecimal packSize,
                           Long priceMinor, BigDecimal onHand, BigDecimal reorderLevel,
                           boolean lowStock, BigDecimal averageCostMinor) {

    static InventoryRow of(Item item, ShopInventory row, boolean includeCost) {
        BigDecimal onHand = row == null ? BigDecimal.ZERO : row.getOnHand();
        BigDecimal reorder = row == null ? BigDecimal.ZERO : row.getReorderLevel();
        return new InventoryRow(
                item.getId(), item.getName(), item.getUnit(), item.getPackName(), item.getPackSize(),
                row == null ? null : row.getPriceMinor(),
                onHand, reorder,
                // the doc's rule: on hand <= reorder level. A shop that has not listed the item yet is not flagged.
                row != null && onHand.compareTo(reorder) <= 0,
                includeCost && row != null ? row.getAverageCost() : null);
    }
}