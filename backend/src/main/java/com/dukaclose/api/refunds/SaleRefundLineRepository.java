package com.dukaclose.api.refunds;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SaleRefundLineRepository extends JpaRepository<SaleRefundLine, UUID> {
    List<SaleRefundLine> findByRefundIdIn(Collection<UUID> refundIds);

    /** quantity and money already refunded, per sale line, across every refund of the sale */
    @Query("select l.saleLineId, sum(l.quantity), sum(l.lineRefundMinor) from SaleRefundLine l "
            + "where l.saleId = :saleId group by l.saleLineId")
    List<Object[]> sumsBySaleLine(@Param("saleId") UUID saleId);
}
