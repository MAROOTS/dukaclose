// DebtPaymentRepository.java
package com.dukaclose.api.deni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {

    Optional<DebtPayment> findByShopIdAndIdempotencyKey(UUID shopId, UUID idempotencyKey);

    List<DebtPayment> findTop100ByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /** What was collected on one shop day, by method. Phase 5 uses this for the drawer reconciliation. */
    @Query("select p.method, sum(p.amountMinor) from DebtPayment p where p.shopDayId = :dayId group by p.method")
    List<Object[]> sumByMethod(@Param("dayId") UUID dayId);
}