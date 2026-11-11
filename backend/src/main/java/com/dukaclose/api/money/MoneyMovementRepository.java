// MoneyMovementRepository.java
package com.dukaclose.api.money;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyMovementRepository extends JpaRepository<MoneyMovement, UUID> {

    Optional<MoneyMovement> findByShopIdAndIdempotencyKey(UUID shopId, UUID key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MoneyMovement m where m.id = :id and m.shopId = :shopId")
    Optional<MoneyMovement> findForUpdate(@Param("id") UUID id, @Param("shopId") UUID shopId);

    Page<MoneyMovement> findByShopDayId(UUID shopDayId, Pageable pageable);
}