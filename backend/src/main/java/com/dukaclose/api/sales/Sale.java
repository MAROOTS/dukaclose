// Sale.java
package com.dukaclose.api.sales;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sales")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sale extends BaseEntity {

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID shopDayId;
    @Column(nullable = false) private long receiptNo;
    @Column(nullable = false) private UUID cashierId;
    @Column(nullable = false) private long totalMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SaleStatus status = SaleStatus.COMPLETED;

    @Column(nullable = false) private UUID idempotencyKey;
    @Column(nullable = false, length = 64) private String requestHash;

    public Sale(UUID businessId, UUID shopId, UUID shopDayId, long receiptNo, UUID cashierId,
                long totalMinor, UUID idempotencyKey, String requestHash) {
        this.businessId = businessId;
        this.shopId = shopId;
        this.shopDayId = shopDayId;
        this.receiptNo = receiptNo;
        this.cashierId = cashierId;
        this.totalMinor = totalMinor;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void markReversed() {
        this.status = SaleStatus.REVERSED;
    }
}