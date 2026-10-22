// Shop.java
package com.dukaclose.api.business;

import com.dukaclose.api.common.BaseEntity;
import com.dukaclose.api.common.RecordStatus;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shops")
public class Shop extends BaseEntity {

    @Column(nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected Shop() {}

    public Shop(UUID businessId, String name) {
        this.businessId = businessId;
        this.name = name;
    }

    public UUID getBusinessId() { return businessId; }
    public String getName() { return name; }
    public RecordStatus getStatus() { return status; }
}