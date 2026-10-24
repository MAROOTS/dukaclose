
package com.dukaclose.api.inventory;

import com.dukaclose.api.common.BaseEntity;
import com.dukaclose.api.common.RecordStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    @Column(nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Unit unit;

    @Column(length = 40)
    private String packName;

    @Column(precision = 14, scale = 3)
    private BigDecimal packSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    public Item(UUID businessId, String name, Unit unit, String packName, BigDecimal packSize) {
        this.businessId = businessId;
        this.name = name;
        this.unit = unit;
        this.packName = packName;
        this.packSize = packSize;
    }

    /** The unit is deliberately not editable. */
    public void update(String name, String packName, BigDecimal packSize, boolean active) {
        this.name = name;
        this.packName = packName;
        this.packSize = packSize;
        this.status = active ? RecordStatus.ACTIVE : RecordStatus.INACTIVE;
    }
}