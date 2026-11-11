package com.dukaclose.api.money;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "money_movement_reversals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyMovementReversal extends BaseEntity {

    @Column(nullable = false) private UUID movementId;
    @Column(nullable = false) private UUID shopDayId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false, length = 200) private String reason;
    @Column(nullable = false) private UUID reversedBy;

    public MoneyMovementReversal(UUID movementId, UUID shopDayId, UUID shopId, String reason, UUID reversedBy) {
        this.movementId = movementId;
        this.shopDayId = shopDayId;
        this.shopId = shopId;
        this.reason = reason;
        this.reversedBy = reversedBy;
    }
}