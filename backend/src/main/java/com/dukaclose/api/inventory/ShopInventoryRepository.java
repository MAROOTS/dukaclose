// ShopInventoryRepository.java
package com.dukaclose.api.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopInventoryRepository extends JpaRepository<ShopInventory, UUID> {
    List<ShopInventory> findByShopId(UUID shopId);
    Optional<ShopInventory> findByShopIdAndItemId(UUID shopId, UUID itemId);
    boolean existsByItemIdAndOnHandGreaterThan(UUID itemId, BigDecimal quantity);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShopInventory s where s.shopId = :shopId and s.itemId = :itemId")
    Optional<ShopInventory> findForUpdate(@Param("shopId") UUID shopId, @Param("itemId") UUID itemId);
}