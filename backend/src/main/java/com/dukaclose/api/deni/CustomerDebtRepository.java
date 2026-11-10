package com.dukaclose.api.deni;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerDebtRepository extends JpaRepository<CustomerDebt, UUID> {

    Optional<CustomerDebt> findBySaleId(UUID saleId);
    List<CustomerDebt> findBySaleIdIn(Collection<UUID> saleIds);

    @Query("select d.customerId, sum(d.originalAmountMinor) from CustomerDebt d "
            + "where d.customerId in :ids and d.status = com.dukaclose.api.deni.DebtStatus.ACTIVE group by d.customerId")
    List<Object[]> activeTotals(@Param("ids") Collection<UUID> ids);
}