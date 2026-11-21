// SaleRefundLine.java
package com.dukaclose.api.refunds;

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
@Table(name = "sale_refund_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleRefundLine extends BaseEntity {

    @Column(nullable = false) private UUID refundId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID saleId;
    @Column(nullable = false) private UUID saleLineId;
    @Column(nullable = false) private UUID itemId;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal quantity;
    @Column(nullable = false) private long unitPriceMinor;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal unitCost;
    @Column(nullable = false) private long lineRefundMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Disposition disposition;

    public SaleRefundLine(UUID refundId, UUID shopId, UUID saleId, UUID saleLineId, UUID itemId,
                          BigDecimal quantity, long unitPriceMinor, BigDecimal unitCost, long lineRefundMinor,
                          Disposition disposition) {
        this.refundId = refundId;
        this.shopId = shopId;
        this.saleId = saleId;
        this.saleLineId = saleLineId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.unitCost = unitCost;
        this.lineRefundMinor = lineRefundMinor;
        this.disposition = disposition;
    }
}