// RefundOptions.java
package com.dukaclose.api.refunds;

import com.dukaclose.api.inventory.Unit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** What can still be refunded on one sale, and what was refunded already. blockedReason: REVERSED or SALE_DAY_OPEN. */
public record RefundOptions(UUID saleId, long receiptNo, boolean refundable, String blockedReason,
                            long saleTotalMinor, long debtOutstandingMinor, List<Line> lines,
                            List<RefundResponse> refunds) {

    public record Line(UUID saleLineId, UUID itemId, String name, Unit unit, BigDecimal soldQuantity,
                       long unitPriceMinor, BigDecimal refundedQuantity, BigDecimal refundableQuantity) {}
}