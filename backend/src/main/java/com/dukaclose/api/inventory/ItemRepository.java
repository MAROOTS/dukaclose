package com.dukaclose.api.inventory;

import com.dukaclose.api.common.RecordStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findByBusinessIdOrderByNameAsc(UUID businessId);
    List<Item> findByBusinessIdAndStatusOrderByNameAsc(UUID businessId, RecordStatus status);
    Optional<Item> findByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
    Optional<Item> findByIdAndBusinessId(UUID id, UUID businessId);
}