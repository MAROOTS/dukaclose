// DebtWriteOffRepository.java
package com.dukaclose.api.deni;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtWriteOffRepository extends JpaRepository<DebtWriteOff, UUID> {

    boolean existsByDebtId(UUID debtId);

    List<DebtWriteOff> findTop100ByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("select w.debtId, sum(w.amountMinor) from DebtWriteOff w where w.debtId in :ids group by w.debtId")
    List<Object[]> sumByDebt(@Param("ids") Collection<UUID> ids);

    @Query("select w.customerId, sum(w.amountMinor) from DebtWriteOff w where w.customerId in :ids group by w.customerId")
    List<Object[]> sumByCustomer(@Param("ids") Collection<UUID> ids);
}