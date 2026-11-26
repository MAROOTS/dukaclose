// OwnerBoards.java
package com.dukaclose.api.reports;

import com.dukaclose.api.inventory.Unit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OwnerBoards {
    private OwnerBoards() {}

    /** Every difference is flagged (flagged = differenceMinor != 0). Negative means the drawer was short. */
    public record CloseRow(UUID shopId, String shopName, LocalDate businessDate, Instant closedAt, String closedByName,
                           long countedMinor, long expectedMinor, long differenceMinor, boolean flagged,
                           String note, long salesTotalMinor) {}

    /** state: OPEN, CLOSED (today), NOT_OPEN, or NEEDS_CLOSE (an earlier day was never closed). */
    public record ShopState(UUID shopId, String shopName, String state, LocalDate openDate, String openedByName,
                            Long openingFloatMinor, Long floatDifferenceMinor, CloseRow lastClose) {}

    public record CloseBoard(LocalDate today, int days, List<ShopState> shops, long needsCloseCount,
                             long flaggedCount, List<CloseRow> recentCloses) {}

    /** Days overdue 1-7, 8-30 and 31 or more. notDue includes anything due today. */
    public record Buckets(long notDueMinor, long days1to7Minor, long days8to30Minor, long days31PlusMinor) {}

    public record AgeingRow(UUID customerId, String name, String phone, long outstandingMinor, long overdueMinor,
                            int oldestOverdueDays, LocalDate earliestDueDate, Long creditLimitMinor, boolean overLimit,
                            Instant lastPaymentAt, Long lastPaymentMinor) {}

    public record DeniAgeing(LocalDate today, Buckets buckets, long outstandingMinor, long overdueMinor,
                             long customerCount, List<AgeingRow> items, int page, int size, int totalPages) {}

    public record LowStockRow(UUID shopId, String shopName, UUID itemId, String itemName, Unit unit,
                              BigDecimal onHand, BigDecimal reorderLevel, Long priceMinor, Instant lastMovementAt) {}

    public record LowStockPage(List<LowStockRow> items, int page, int size, long totalElements, int totalPages) {}
}