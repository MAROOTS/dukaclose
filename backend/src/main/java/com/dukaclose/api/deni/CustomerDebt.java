// CustomerDebt.java
package com.dukaclose.api.deni;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "customer_debts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerDebt extends BaseEntity {

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID saleId;
    @Column(nullable = false) private UUID customerId;
    @Column(nullable = false) private long originalAmountMinor;
    @Column(nullable = false) private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DebtStatus status = DebtStatus.ACTIVE;

    public CustomerDebt(UUID businessId, UUID shopId, UUID saleId, UUID customerId,
                        long originalAmountMinor, LocalDate dueDate) {
        this.businessId = businessId;
        this.shopId = shopId;
        this.saleId = saleId;
        this.customerId = customerId;
        this.originalAmountMinor = originalAmountMinor;
        this.dueDate = dueDate;
    }

    public void markVoid() {
        this.status = DebtStatus.VOID;
    }
}