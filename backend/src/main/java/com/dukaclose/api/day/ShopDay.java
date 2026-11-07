package com.dukaclose.api.day;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shop_days")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopDay extends BaseEntity {

    @Column(nullable = false) private UUID shopId;
    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayStatus status = DayStatus.OPEN;

    @Column(nullable = false) private long openingFloatMinor;
    @Column(nullable = false) private UUID openedBy;

    public ShopDay(UUID shopId, UUID businessId, LocalDate businessDate, long openingFloatMinor, UUID openedBy) {
        this.shopId = shopId;
        this.businessId = businessId;
        this.businessDate = businessDate;
        this.openingFloatMinor = openingFloatMinor;
        this.openedBy = openedBy;
    }

    public void markClosed() {
        this.status = DayStatus.CLOSED;
    }
}