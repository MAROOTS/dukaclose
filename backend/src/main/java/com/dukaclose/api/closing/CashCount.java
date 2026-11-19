// CashCount.java
package com.dukaclose.api.closing;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "cash_counts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashCount extends BaseEntity {

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID shopDayId;
    @Column(nullable = false) private long countedMinor;
    @Column(nullable = false) private long expectedMinor;
    @Column(nullable = false, length = 64) private String activityToken;
    @Column(nullable = false) private UUID countedBy;
    @Column(nullable = false) private UUID idempotencyKey;
    @Column(nullable = false, length = 64) private String requestHash;

    public CashCount(UUID businessId, UUID shopId, UUID shopDayId, long countedMinor, long expectedMinor,
                     String activityToken, UUID countedBy, UUID idempotencyKey, String requestHash) {
        this.businessId = businessId;
        this.shopId = shopId;
        this.shopDayId = shopDayId;
        this.countedMinor = countedMinor;
        this.expectedMinor = expectedMinor;
        this.activityToken = activityToken;
        this.countedBy = countedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }
}