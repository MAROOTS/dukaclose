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

    boolean existsByDebtId(UUID debtId);

    @Query("select a.debtId, sum(a.amountMinor) from DebtPaymentAllocation a where a.debtId in :ids group by a.debtId")
    List<Object[]> sumByDebt(@Param("ids") Collection<UUID> ids);

    @Query("select a.customerId, sum(a.amountMinor) from DebtPaymentAllocation a where a.customerId in :ids group by a.customerId")
    List<Object[]> sumByCustomer(@Param("ids") Collection<UUID> ids);
}