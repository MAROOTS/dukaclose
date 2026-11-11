package com.dukaclose.api.money;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyMovementReversalRepository extends JpaRepository<MoneyMovementReversal, UUID> {
    boolean existsByMovementId(UUID movementId);
    List<MoneyMovementReversal> findByMovementIdIn(Collection<UUID> movementIds);
}