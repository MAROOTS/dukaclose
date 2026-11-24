package com.dukaclose.api.refunds;

import java.util.List;
import java.util.UUID;

public record RefundQuote(long totalMinor, long debtCreditMinor, long payoutMinor, List<Line> lines) {
    public record Line(UUID saleLineId, long lineRefundMinor) {}
}
