// DebtCreditRepository.java
package com.dukaclose.api.deni;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtCreditRepository extends JpaRepository<DebtCredit, UUID> {

    boolean existsByDebtId(UUID debtId);

    @Query("select c.debtId, sum(c.amountMinor) from DebtCredit c where c.debtId in :ids group by c.debtId")
    List<Object[]> sumByDebt(@Param("ids") Collection<UUID> ids);

    @Query("select c.customerId, sum(c.amountMinor) from DebtCredit c where c.customerId in :ids group by c.customerId")
    List<Object[]> sumByCustomer(@Param("ids") Collection<UUID> ids);
}