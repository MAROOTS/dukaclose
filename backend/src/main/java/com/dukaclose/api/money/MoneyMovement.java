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
@Table(name = "money_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyMovement extends BaseEntity {

    @Column(insertable = false, updatable = false)
    private Long seq;                       // assigned by the database

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID shopDayId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private MoneyDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MoneyCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MoneyMethod method;

    @Column(nullable = false) private long amountMinor;
    @Column(nullable = false, length = 200) private String note;
    @Column(nullable = false) private UUID createdBy;
    @Column(nullable = false) private UUID idempotencyKey;
    @Column(nullable = false, length = 64) private String requestHash;

    public MoneyMovement(UUID businessId, UUID shopId, UUID shopDayId, MoneyCategory category, MoneyMethod method,
                         long amountMinor, String note, UUID createdBy, UUID idempotencyKey, String requestHash) {
        this.businessId = businessId;
        this.shopId = shopId;
        this.shopDayId = shopDayId;
        this.direction = category.direction();
        this.category = category;
        this.method = method;
        this.amountMinor = amountMinor;
        this.note = note;
        this.createdBy = createdBy;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }
}