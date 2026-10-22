// ShopMembership.java
package com.dukaclose.api.business;

import com.dukaclose.api.common.BaseEntity;
import com.dukaclose.api.common.RecordStatus;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shop_memberships")
public class ShopMembership extends BaseEntity {

    @Column(nullable = false)
    private UUID shopId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected ShopMembership() {}

    public ShopMembership(UUID shopId, UUID userId, Role role) {
        this.shopId = shopId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getShopId() { return shopId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public RecordStatus getStatus() { return status; }
}