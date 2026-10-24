package com.dukaclose.api.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Optional<StockMovement> findByShopIdAndRequestId(UUID shopId, UUID requestId);

    boolean existsByShopIdAndItemId(UUID shopId, UUID itemId);

    List<StockMovement> findByShopIdAndItemIdOrderBySeqDesc(UUID shopId, UUID itemId, Pageable pageable);

    @Query("select m.itemId, sum(m.quantityDelta) from StockMovement m where m.shopId = :shopId group by m.itemId")
    List<Object[]> sumByItem(@Param("shopId") UUID shopId);
}