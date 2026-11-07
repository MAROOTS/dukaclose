// ShopDayRepository.java
package com.dukaclose.api.day;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopDayRepository extends JpaRepository<ShopDay, UUID> {
    Optional<ShopDay> findByShopIdAndStatus(UUID shopId, DayStatus status);
    boolean existsByShopIdAndBusinessDate(UUID shopId, LocalDate businessDate);
}