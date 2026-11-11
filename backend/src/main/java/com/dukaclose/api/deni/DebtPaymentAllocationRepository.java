// DebtPaymentAllocationRepository.java
package com.dukaclose.api.deni;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtPaymentAllocationRepository extends JpaRepository<DebtPaymentAllocation, UUID> {

    List<DebtPaymentAllocation> findByPaymentIdIn(Collection<UUID> paymentIds);

    /** True when a repayment that still stands has been applied to this debt. */
    @Query("select count(a) > 0 from DebtPaymentAllocation a where a.debtId = :debtId "
            + "and not exists (select 1 from DebtPaymentReversal r where r.paymentId = a.paymentId)")
    boolean existsActiveByDebtId(@Param("debtId") UUID debtId);

    @Query("select a.debtId, sum(a.amountMinor) from DebtPaymentAllocation a where a.debtId in :ids "
            + "and not exists (select 1 from DebtPaymentReversal r where r.paymentId = a.paymentId) group by a.debtId")
    List<Object[]> sumByDebt(@Param("ids") Collection<UUID> ids);

    @Query("select a.customerId, sum(a.amountMinor) from DebtPaymentAllocation a where a.customerId in :ids "
            + "and not exists (select 1 from DebtPaymentReversal r where r.paymentId = a.paymentId) group by a.customerId")
    List<Object[]> sumByCustomer(@Param("ids") Collection<UUID> ids);
}