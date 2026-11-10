// DebtPayment.java
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
@Table(name = "debt_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebtPayment extends BaseEntity {

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID shopDayId;
    @Column(nullable = false) private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CollectionMethod method;

    @Column(nullable = false) private long amountMinor;
    @Column(nullable = false) private UUID receivedBy;
    @Column(nullable = false) private UUID idempotencyKey;
    @Column(nullable = false, length = 64) private String requestHash;

    public DebtPayment(UUID businessId, UUID shopId, UUID shopDayId, UUID customerId, CollectionMethod method,
                       long amountMinor, UUID receivedBy, UUID idempotencyKey, String requestHash) {
        this.businessId = businessId;
        this.shopId = shopId;
        this.shopDayId = shopDayId;
        this.customerId = customerId;
        this.method = method;
        this.amountMinor = amountMinor;
        this.receivedBy = receivedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }
}