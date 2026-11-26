// DrillDownRows.java
package com.dukaclose.api.reports;

import com.dukaclose.api.inventory.Unit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One row per underlying record. Open the record itself through its shop's own endpoint. */
public final class DrillDownRows {
    private DrillDownRows() {}

    public record Payment(String method, long amountMinor) {}

    public record SaleRow(UUID id, UUID shopId, String shopName, long receiptNo, LocalDate businessDate,
                          Instant createdAt, String cashierName, long totalMinor, String status, List<Payment> payments) {}

    public record MoneyRow(UUID id, UUID shopId, String shopName, LocalDate businessDate, Instant createdAt,
                           String direction, String category, String method, long amountMinor, String note,
                           String createdByName, boolean reversed, boolean operatingExpense) {}

    public record CollectionRow(UUID id, UUID shopId, String shopName, LocalDate businessDate, Instant createdAt,
                                UUID customerId, String customerName, String method, long amountMinor,
                                String receivedByName, boolean reversed) {}

    public record RefundRow(UUID id, UUID shopId, String shopName, LocalDate businessDate, Instant createdAt,
                            UUID saleId, long saleReceiptNo, LocalDate saleDate, long totalMinor,
                            long debtCreditMinor, String reason, String refundedByName) {}

    public record WriteOffRow(UUID id, UUID shopId, String shopName, LocalDate businessDate, Instant createdAt,
                              UUID customerId, String customerName, UUID saleId, long saleReceiptNo,
                              long amountMinor, String reason, String writtenOffByName) {}

    /** valueMinor is this row's own cost, rounded. The page summary rounds per shop and day, like the dashboard. */
    public record AdjustmentRow(UUID id, UUID shopId, String shopName, UUID itemId, String itemName, Unit unit,
                                LocalDate businessDate, Instant createdAt, BigDecimal quantityDelta, String reason,
                                String note, String byName, long valueMinor, boolean shrinkage) {}
}