// DebtCredit.java
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
@Table(name = "debt_credits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebtCredit extends BaseEntity {

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID debtId;
    @Column(nullable = false) private UUID customerId;
    @Column(nullable = false) private UUID refundId;
    @Column(nullable = false) private long amountMinor;

    public DebtCredit(UUID businessId, UUID debtId, UUID customerId, UUID refundId, long amountMinor) {
        this.businessId = businessId;
        this.debtId = debtId;
        this.customerId = customerId;
        this.refundId = refundId;
        this.amountMinor = amountMinor;
    }
}