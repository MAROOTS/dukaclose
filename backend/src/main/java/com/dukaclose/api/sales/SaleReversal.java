// SaleReversal.java
package com.dukaclose.api.sales;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "sale_reversals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleReversal extends BaseEntity {

    @Column(nullable = false) private UUID saleId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID shopDayId;
    @Column(nullable = false, length = 200) private String reason;
    @Column(nullable = false) private UUID reversedBy;

    public SaleReversal(UUID saleId, UUID shopId, UUID businessId, UUID shopDayId, String reason, UUID reversedBy) {
        this.saleId = saleId;
        this.shopId = shopId;
        this.businessId = businessId;
        this.shopDayId = shopDayId;
        this.reason = reason;
        this.reversedBy = reversedBy;
    }
}