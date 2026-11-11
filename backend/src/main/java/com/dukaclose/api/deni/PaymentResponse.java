// PaymentResponse.java
package com.dukaclose.api.deni;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID customerId, String customerName, CollectionMethod method,
                              long amountMinor, UUID shopId, LocalDate businessDate, String receivedByName,
                              UUID receivedById, Instant createdAt, List<Allocation> allocations,
                              long customerOutstandingMinor, Reversal reversal) {

    /** debtBalanceMinor is what is still owed on that debt right now. */
    public record Allocation(UUID debtId, long receiptNo, long amountMinor, long debtBalanceMinor, LocalDate dueDate) {}

    /** Null unless the repayment was reversed. The original stays exactly as it was recorded. */
    public record Reversal(String reason, String reversedByName, Instant reversedAt) {}
}