// SaleReversalRepository.java
package com.dukaclose.api.sales;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleReversalRepository extends JpaRepository<SaleReversal, UUID> {
    List<SaleReversal> findBySaleIdIn(Collection<UUID> saleIds);
}