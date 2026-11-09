package com.dukaclose.api.sales;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
    Optional<Sale> findByShopIdAndIdempotencyKey(UUID shopId, UUID idempotencyKey);
    Optional<Sale> findByIdAndShopId(UUID id, UUID shopId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id and s.shopId = :shopId")
    Optional<Sale> findForUpdate(@Param("id") UUID id, @Param("shopId") UUID shopId);

    Page<Sale> findByShopDayId(UUID shopDayId, Pageable pageable);
}