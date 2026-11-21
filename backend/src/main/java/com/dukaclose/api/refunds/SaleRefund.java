// SaleRefund.java
package com.dukaclose.api.refunds;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "sale_refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleRefund extends BaseEntity {

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID shopDayId;
    @Column(nullable = false) private UUID saleId;
    @Column(nullable = false) private UUID saleDayId;
    @Column(nullable = false) private long totalMinor;
    @Column(nullable = false) private long debtCreditMinor;
    @Column(nullable = false, length = 200) private String reason;
    @Column(nullable = false) private UUID refundedBy;
    @Column(nullable = false) private UUID idempotencyKey;
    @Column(nullable = false, length = 64) private String requestHash;

    public SaleRefund(UUID businessId, UUID shopId, UUID shopDayId, UUID saleId, UUID saleDayId, long totalMinor,
                      long debtCreditMinor, String reason, UUID refundedBy, UUID idempotencyKey, String requestHash) {
        this.businessId = businessId;
        this.shopId = shopId;
        this.shopDayId = shopDayId;
        this.saleId = saleId;
        this.saleDayId = saleDayId;
        this.totalMinor = totalMinor;
        this.debtCreditMinor = debtCreditMinor;
        this.reason = reason;
        this.refundedBy = refundedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }
}