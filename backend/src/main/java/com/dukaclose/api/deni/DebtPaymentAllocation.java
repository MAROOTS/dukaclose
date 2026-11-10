// DebtPaymentAllocation.java
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
@Table(name = "debt_payment_allocations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebtPaymentAllocation extends BaseEntity {

    @Column(nullable = false) private UUID paymentId;
    @Column(nullable = false) private UUID debtId;
    @Column(nullable = false) private UUID customerId;
    @Column(nullable = false) private long amountMinor;

    public DebtPaymentAllocation(UUID paymentId, UUID debtId, UUID customerId, long amountMinor) {
        this.paymentId = paymentId;
        this.debtId = debtId;
        this.customerId = customerId;
        this.amountMinor = amountMinor;
    }
}