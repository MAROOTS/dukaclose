// SaleAllocation.java
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
@Table(name = "sale_allocations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleAllocation extends BaseEntity {

    @Column(nullable = false) private UUID saleId;
    @Column(nullable = false) private UUID shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod method;

    @Column(nullable = false) private long amountMinor;

    public SaleAllocation(UUID saleId, UUID shopId, PaymentMethod method, long amountMinor) {
        this.saleId = saleId;
        this.shopId = shopId;
        this.method = method;
        this.amountMinor = amountMinor;
    }
}