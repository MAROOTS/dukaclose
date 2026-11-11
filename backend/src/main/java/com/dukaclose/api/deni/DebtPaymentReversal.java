// DebtPaymentReversal.java
package com.dukaclose.api.deni;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "debt_payment_reversals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebtPaymentReversal extends BaseEntity {

    @Column(nullable = false) private UUID paymentId;
    @Column(nullable = false) private UUID shopDayId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false, length = 200) private String reason;
    @Column(nullable = false) private UUID reversedBy;

    public DebtPaymentReversal(UUID paymentId, UUID shopDayId, UUID shopId, String reason, UUID reversedBy) {
        this.paymentId = paymentId;
        this.shopDayId = shopDayId;
        this.shopId = shopId;
        this.reason = reason;
        this.reversedBy = reversedBy;
    }
}