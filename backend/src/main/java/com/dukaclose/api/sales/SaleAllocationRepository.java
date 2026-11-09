package com.dukaclose.api.sales;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleAllocationRepository extends JpaRepository<SaleAllocation, UUID> {
    List<SaleAllocation> findBySaleId(UUID saleId);
    List<SaleAllocation> findBySaleIdIn(Collection<UUID> saleIds);

}