package com.dukaclose.api.deni;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DebtViews {
    private DebtViews() {}

    public record LedgerRow(UUID customerId, String name, String phone, long outstandingMinor, long overdueMinor,
                            LocalDate nextDueDate, Long creditLimitMinor, boolean overLimit) {}

    public record LedgerPage(LocalDate today, long totalOutstandingMinor, long totalOverdueMinor, long customerCount,
                             List<LedgerRow> items, int page, int size, int totalPages) {}

    public record DebtLine(UUID debtId, UUID saleId, long receiptNo, String shopName, long originalMinor,
                           long paidMinor, long writtenOffMinor, long balanceMinor, LocalDate dueDate,
                           boolean overdue, boolean voided, Instant createdAt) {}

    public record PaymentAllocationLine(UUID debtId, long receiptNo, long amountMinor) {}

    public record PaymentReversalView(String reason, String reversedByName, Instant reversedAt) {}

    public record PaymentLine(UUID paymentId, CollectionMethod method, long amountMinor, String receivedByName,
                              String shopName, Instant createdAt, List<PaymentAllocationLine> allocations,
                              PaymentReversalView reversal) {}

    public record WriteOffLine(UUID debtId, long receiptNo, long amountMinor, String reason,
                               String writtenOffByName, Instant createdAt) {}

    public record CustomerLedger(CustomerView customer, long overdueMinor, List<DebtLine> debts,
                                 List<PaymentLine> payments, List<WriteOffLine> writeOffs) {}

    public record WriteOffResponse(DebtLine debt, long customerOutstandingMinor) {}

    public record PaymentPage(LocalDate date, List<PaymentResponse> items, int page, int size,
                              long totalElements, int totalPages) {}
}