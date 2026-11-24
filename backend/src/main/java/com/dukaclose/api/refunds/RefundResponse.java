// RefundResponse.java
package com.dukaclose.api.refunds;

import com.dukaclose.api.inventory.Unit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** No cost fields: cashiers can read refunds too. receiptNo is the ORIGINAL sale's. */
public record RefundResponse(UUID id, UUID saleId, long receiptNo, UUID shopId, LocalDate businessDate,
                             LocalDate saleDate, long totalMinor, long debtCreditMinor, String reason,
                             String refundedByName, Instant createdAt, List<Line> lines, List<Payout> payouts) {

    public record Line(UUID saleLineId, UUID itemId, String name, Unit unit, BigDecimal quantity,
                       long unitPriceMinor, long lineRefundMinor, Disposition disposition) {}

    public record Payout(PayoutMethod method, long amountMinor) {}
}