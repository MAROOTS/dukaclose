package com.dukaclose.api.refunds;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRefundRepository extends JpaRepository<SaleRefund, UUID> {
    Optional<SaleRefund> findByShopIdAndIdempotencyKey(UUID shopId, UUID key);
    List<SaleRefund> findBySaleIdOrderByCreatedAtDesc(UUID saleId);
}