// SaleRefundPayout.java
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
@Table(name = "sale_refund_payouts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleRefundPayout extends BaseEntity {

    @Column(nullable = false) private UUID refundId;
    @Column(nullable = false) private UUID shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayoutMethod method;

    @Column(nullable = false) private long amountMinor;

    public SaleRefundPayout(UUID refundId, UUID shopId, PayoutMethod method, long amountMinor) {
        this.refundId = refundId;
        this.shopId = shopId;
        this.method = method;
        this.amountMinor = amountMinor;
    }
}