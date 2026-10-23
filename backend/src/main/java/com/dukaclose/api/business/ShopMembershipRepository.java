package com.dukaclose.api.business;

import com.dukaclose.api.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopMembershipRepository extends JpaRepository<ShopMembership, UUID> {
    List<ShopMembership> findByUserId(UUID userId);
    List<ShopMembership> findByUserIdAndStatus(UUID userId, RecordStatus status);
    boolean existsByShopIdAndUserIdAndStatus(UUID shopId, UUID userId, RecordStatus status);
    boolean existsByShopIdInAndUserId(Collection<UUID> shopIds, UUID userId);
    List<ShopMembership> findByShopIdAndStatus(UUID shopId, RecordStatus status);
    Optional<ShopMembership> findByShopIdAndUserId(UUID shopId, UUID userId);
}
