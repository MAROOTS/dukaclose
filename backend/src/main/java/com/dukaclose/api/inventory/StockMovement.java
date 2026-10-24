// StockMovement.java
package com.dukaclose.api.inventory;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement extends BaseEntity {

    @Column(insertable = false, updatable = false)
    private Long seq;                       // assigned by the database

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID itemId;
    @Column(nullable = false) private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType movementType;

    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal quantityDelta;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal balanceAfter;
    @Column(precision = 18, scale = 4) private BigDecimal unitCost;
    private Long totalCostMinor;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AdjustmentReason reason;

    @Column(length = 200) private String note;
    @Column(nullable = false) private UUID createdBy;

    public StockMovement(UUID businessId, UUID shopId, UUID itemId, UUID requestId, MovementType movementType,
                         BigDecimal quantityDelta, BigDecimal balanceAfter, BigDecimal unitCost,
                         Long totalCostMinor, AdjustmentReason reason, String note, UUID createdBy) {
        this.businessId = businessId;
        this.shopId = shopId;
        this.itemId = itemId;
        this.requestId = requestId;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.balanceAfter = balanceAfter;
        this.unitCost = unitCost;
        this.totalCostMinor = totalCostMinor;
        this.reason = reason;
        this.note = note;
        this.createdBy = createdBy;
    }
}