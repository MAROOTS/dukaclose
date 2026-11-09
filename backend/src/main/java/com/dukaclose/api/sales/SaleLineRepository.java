package com.dukaclose.api.sales;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleLineRepository extends JpaRepository<SaleLine, UUID> {
    List<SaleLine> findBySaleIdOrderByLineNo(UUID saleId);
    List<SaleLine> findBySaleIdIn(Collection<UUID> saleIds);

}