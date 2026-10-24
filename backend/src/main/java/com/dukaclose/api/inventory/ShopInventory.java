package com.dukaclose.api.inventory;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shop_inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopInventory extends BaseEntity {

    @Column(nullable = false)
    private UUID shopId;

    @Column(nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private UUID itemId;

    /** Selling price in cents (KSh 190.00 = 19000). Null means "not priced yet". */
    private Long priceMinor;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal onHand = BigDecimal.ZERO;

    /** Weighted-average cost per selling unit, in cents, kept to 4 decimals. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    public ShopInventory(UUID shopId, UUID businessId, UUID itemId, Long priceMinor) {
        this.shopId = shopId;
        this.businessId = businessId;
        this.itemId = itemId;
        this.priceMinor = priceMinor;
    }

    public void update(Long priceMinor, BigDecimal reorderLevel) {
        this.priceMinor = priceMinor;
        this.reorderLevel = reorderLevel;
    }

    public void applyStockIn(BigDecimal newOnHand, BigDecimal newAverageCost) {
        this.onHand = newOnHand;
        this.averageCost = newAverageCost;
    }

    public void applyAdjustment(BigDecimal newOnHand) {
        this.onHand = newOnHand;
    }
}