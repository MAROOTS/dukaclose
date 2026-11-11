package com.dukaclose.api.deni;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtPaymentReversalRepository extends JpaRepository<DebtPaymentReversal, UUID> {
    boolean existsByPaymentId(UUID paymentId);
    List<DebtPaymentReversal> findByPaymentIdIn(Collection<UUID> paymentIds);
}