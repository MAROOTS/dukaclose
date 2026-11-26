// DashboardResponse.java
package com.dukaclose.api.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        String period, LocalDate asOf, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
        List<ShopRef> shops, Totals totals, Totals previous, List<ShopRow> byShop, List<DayRow> byDay,
        DeniNow deni) {

    public record ShopRef(UUID id, String name) {}

    /** Everything is in cents. netSales = sales - refunds. costOfGoodsSold is after goods put back on the shelf. */
    public record Totals(
            long salesCount, long salesTotalMinor, long cashSalesMinor, long pochiSalesMinor, long tillSalesMinor,
            long deniSalesMinor,
            long refundsCount, long refundsTotalMinor, long netSalesMinor,
            long collectionsTotalMinor, long cashCollectionsMinor, long pochiCollectionsMinor, long tillCollectionsMinor,
            long operatingExpensesMinor, long costOfGoodsSoldMinor, long grossProfitMinor, long estimatedProfitMinor,
            long writeOffsMinor, long shrinkageMinor) {}

    public record ShopRow(UUID shopId, String name, Totals totals) {}

    public record DayRow(LocalDate date, Totals totals) {}

    /** What customers owe right now, across the whole business. Not a period figure. */
    public record DeniNow(long outstandingMinor, long overdueMinor, long customerCount) {}
}