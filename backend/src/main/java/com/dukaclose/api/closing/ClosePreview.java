package com.dukaclose.api.closing;

import com.dukaclose.api.inventory.AdjustmentReason;
import com.dukaclose.api.inventory.Unit;
import com.dukaclose.api.money.MoneyCategory;
import com.dukaclose.api.money.MoneyDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the evening close answers, worked out on the server from saved records.
 * drawer is null for a cashier who has not counted (or whose count is out of date); profit and counts are
 * owner-only. Cashiers never receive any cost figure.
 */
public record ClosePreview(
        UUID shopId, String shopName, UUID dayId, LocalDate businessDate, boolean stale,
        Instant openedAt, String openedByName, String activityToken,
        Sales sales, CollectionsBlock collections, Refunds refunds, MoneyBlock money, DeniBlock deni,
        StockBlock stock, Drawer drawer, Profit profit, List<CountLine> counts) {

    /** Sales that stand (reversed ones are shown separately). Deni is part of sales, not money received. */
    public record Sales(long count, long totalMinor, long cashMinor, long pochiMinor, long tillMinor,
                        long deniMinor, long reversedCount, long reversedMinor) {}

    /** Repayments of earlier deni. Never sales. */
    public record CollectionsBlock(long count, long cashMinor, long pochiMinor, long tillMinor,
                                   long totalMinor, long reversedCount) {}

    /** Refunds of earlier days' sales, recorded on this day. They reduce net sales and pay money back. */
    public record Refunds(long count, long totalMinor, long cashMinor, long pochiMinor, long tillMinor,
                          long deniCreditMinor) {}

    public record CategoryTotal(MoneyCategory category, MoneyDirection direction, long totalMinor,
                                boolean operatingExpense) {}

    public record MoneyBlock(long cashOutMinor, long operatingExpensesMinor, long stockPurchasesMinor,
                             long floatMovementsMinor, long otherCashInMinor, long reversedCount,
                             List<CategoryTotal> byCategory) {}

    /** outstanding and overdue are business-wide, including debts from earlier days and other branches. */
    public record DeniBlock(long newTodayMinor, long newTodayCount, long collectedTodayMinor,
                            long outstandingMinor, long overdueMinor, long customerCount) {}

    public record ItemSold(String name, Unit unit, BigDecimal quantity, long totalMinor) {}

    public record LowStock(String name, Unit unit, BigDecimal onHand, BigDecimal reorderLevel) {}

    public record AdjustmentLine(String itemName, Unit unit, BigDecimal quantityDelta, AdjustmentReason reason,
                                 String note, String byName, Instant createdAt) {}

    public record StockBlock(List<ItemSold> itemsSold, List<LowStock> lowStock, List<AdjustmentLine> adjustments) {}

    /** differenceMinor = counted - expected: negative means the drawer is short. Null unless the count is current.
     *  floatDifferenceMinor = opening float - what the last close counted (null on the very first day).
     *  cashRefundsMinor is null in snapshots stored before refunds existed. */
    public record Drawer(long openingFloatMinor, long cashSalesMinor, long cashCollectionsMinor,
                         long otherCashInMinor, long cashOutMinor, long expectedMinor,
                         Long countedMinor, Long differenceMinor, int countNumber, boolean countCurrent,
                         Long carriedOverMinor, Long floatDifferenceMinor, UUID countId, Long cashRefundsMinor) {}

    /** Owner only. writeOffs and shrinkage are shown separately and are NOT subtracted from the estimate.
     *  netSales is after refunds, and costOfGoodsSold is after the cost of goods put back on the shelf. */
    public record Profit(long netSalesMinor, long costOfGoodsSoldMinor, long grossProfitMinor,
                         long operatingExpensesMinor, long estimatedProfitMinor, long shrinkageMinor,
                         long writeOffsMinor) {}

    /** Owner only: every count that was made today, oldest first. */
    public record CountLine(int countNumber, long countedMinor, long expectedMinor, long differenceMinor,
                            String countedByName, Instant createdAt, boolean current) {}

    /** What a cashier may read: the same preview without profit, shrinkage, write-offs or the list of counts. */
    public ClosePreview withoutOwnerFigures() {
        return new ClosePreview(shopId, shopName, dayId, businessDate, stale, openedAt, openedByName, activityToken,
                sales, collections, refunds, money, deni, stock, drawer, null, null);
    }
}