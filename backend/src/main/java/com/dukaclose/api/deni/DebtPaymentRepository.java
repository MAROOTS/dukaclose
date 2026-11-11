// DebtPaymentRepository.java
package com.dukaclose.api.deni;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {

    Optional<DebtPayment> findByShopIdAndIdempotencyKey(UUID shopId, UUID idempotencyKey);

    List<DebtPayment> findTop100ByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Page<DebtPayment> findByShopDayId(UUID shopDayId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DebtPayment p where p.id = :id and p.shopId = :shopId")
    Optional<DebtPayment> findForUpdate(@Param("id") UUID id, @Param("shopId") UUID shopId);

    /** What was collected on one shop day, by method, leaving out reversed repayments. */
    @Query("select p.method, sum(p.amountMinor) from DebtPayment p where p.shopDayId = :dayId "
            + "and not exists (select 1 from DebtPaymentReversal r where r.paymentId = p.id) group by p.method")
    List<Object[]> sumByMethod(@Param("dayId") UUID dayId);
}