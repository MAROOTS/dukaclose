// SaleLine.java
package com.dukaclose.api.sales;

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
@Table(name = "sale_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleLine extends BaseEntity {

    @Column(nullable = false) private UUID saleId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID itemId;
    @Column(nullable = false) private int lineNo;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal quantity;
    @Column(nullable = false) private long unitPriceMinor;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal unitCost;
    @Column(nullable = false) private long lineTotalMinor;
    @Column(nullable = false) private long lineCostMinor;

    public SaleLine(UUID saleId, UUID shopId, UUID businessId, UUID itemId, int lineNo, BigDecimal quantity,
                    long unitPriceMinor, BigDecimal unitCost, long lineTotalMinor, long lineCostMinor) {
        this.saleId = saleId;
        this.shopId = shopId;
        this.businessId = businessId;
        this.itemId = itemId;
        this.lineNo = lineNo;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.unitCost = unitCost;
        this.lineTotalMinor = lineTotalMinor;
        this.lineCostMinor = lineCostMinor;
    }
}