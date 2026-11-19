// CashCountRepository.java
package com.dukaclose.api.closing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashCountRepository extends JpaRepository<CashCount, UUID> {
    Optional<CashCount> findByShopIdAndIdempotencyKey(UUID shopId, UUID idempotencyKey);
    List<CashCount> findByShopDayIdOrderByCreatedAtAsc(UUID shopDayId);
    long countByShopDayIdAndCreatedAtLessThanEqual(UUID shopDayId, Instant createdAt);
}