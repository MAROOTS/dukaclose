// BusinessMembership.java
package com.dukaclose.api.business;

import com.dukaclose.api.common.BaseEntity;
import com.dukaclose.api.common.RecordStatus;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "business_memberships")
public class BusinessMembership extends BaseEntity {

    @Column(nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected BusinessMembership() {}

    public BusinessMembership(UUID businessId, UUID userId, Role role) {
        this.businessId = businessId;
        this.userId = userId;
        this.role = role;
    }

    public void deactivate() {
        this.status = RecordStatus.INACTIVE;
    }

    public UUID getBusinessId() { return businessId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public RecordStatus getStatus() { return status; }
}