// DebtWriteOff.java
package com.dukaclose.api.deni;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "debt_write_offs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebtWriteOff extends BaseEntity {

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID debtId;
    @Column(nullable = false) private UUID customerId;
    @Column(nullable = false) private long amountMinor;
    @Column(nullable = false, length = 200) private String reason;
    @Column(nullable = false) private UUID writtenOffBy;
    @Column(nullable = false) private LocalDate businessDate;

    public DebtWriteOff(UUID businessId, UUID debtId, UUID customerId, long amountMinor, String reason,
                        UUID writtenOffBy, LocalDate businessDate) {
        this.businessId = businessId;
        this.debtId = debtId;
        this.customerId = customerId;
        this.amountMinor = amountMinor;
        this.reason = reason;
        this.writtenOffBy = writtenOffBy;
        this.businessDate = businessDate;
    }
}