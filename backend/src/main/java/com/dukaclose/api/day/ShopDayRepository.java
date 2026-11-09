// ShopDayRepository.java
package com.dukaclose.api.day;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopDayRepository extends JpaRepository<ShopDay, UUID> {
    Optional<ShopDay> findByShopIdAndStatus(UUID shopId, DayStatus status);
    boolean existsByShopIdAndBusinessDate(UUID shopId, LocalDate businessDate);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from ShopDay d where d.shopId = :shopId and d.status = :status")
    Optional<ShopDay> findLockedForShare(@Param("shopId") UUID shopId, @Param("status") DayStatus status);
    Optional<ShopDay> findByShopIdAndBusinessDate(UUID shopId, LocalDate businessDate);
}