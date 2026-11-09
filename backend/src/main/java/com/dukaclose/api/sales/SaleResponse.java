package com.dukaclose.api.sales;

import com.dukaclose.api.inventory.Unit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Deliberately has no cost fields: cashiers never see what an item cost. */
public record SaleResponse(UUID id, long receiptNo, UUID shopId, LocalDate businessDate, SaleStatus status,
                           long totalMinor, UUID cashierId, String cashierName, Instant createdAt,
                           List<Line> lines, List<Allocation> allocations, Reversal reversal) {

    public record Line(UUID itemId, String name, Unit unit, BigDecimal quantity,
                       long unitPriceMinor, long lineTotalMinor) {}

    public record Allocation(PaymentMethod method, long amountMinor) {}

    /** Null unless the sale was reversed. The original sale above stays exactly as it was recorded. */
    public record Reversal(String reason, String reversedByName, Instant reversedAt) {}
}