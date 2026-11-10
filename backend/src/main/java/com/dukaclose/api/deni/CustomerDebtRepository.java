package com.dukaclose.api.deni;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerDebtRepository extends JpaRepository<CustomerDebt, UUID> {

    Optional<CustomerDebt> findBySaleId(UUID saleId);
    List<CustomerDebt> findBySaleIdIn(Collection<UUID> saleIds);

    @Query("select d.customerId, sum(d.originalAmountMinor) from CustomerDebt d "
            + "where d.customerId in :ids and d.status = com.dukaclose.api.deni.DebtStatus.ACTIVE group by d.customerId")
    List<Object[]> activeTotals(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CustomerDebt d where d.customerId = :customerId "
            + "and d.status = com.dukaclose.api.deni.DebtStatus.ACTIVE order by d.id")
    List<CustomerDebt> lockActiveForCustomer(@Param("customerId") UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CustomerDebt d where d.id = :id and d.businessId = :businessId")
    Optional<CustomerDebt> findForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CustomerDebt d where d.saleId = :saleId")
    Optional<CustomerDebt> findBySaleIdForUpdate(@Param("saleId") UUID saleId);

    List<CustomerDebt> findTop100ByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}