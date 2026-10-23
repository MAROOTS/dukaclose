package com.dukaclose.api.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, UUID> {
    List<Shop> findByBusinessId(UUID businessId);
    List<Shop> findByBusinessIdIn(Collection<UUID> businessIds);
    boolean existsByBusinessIdAndName(UUID businessId, String name);

}
