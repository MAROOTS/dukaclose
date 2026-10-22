package com.dukaclose.api.business;

import com.dukaclose.api.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShopMembershipRepository extends JpaRepository<ShopMembership, UUID> {
    List<ShopMembership> findByUserId(UUID userId);
    List<ShopMembership> findByUserIdAndStatus(UUID userId, RecordStatus status);

}
