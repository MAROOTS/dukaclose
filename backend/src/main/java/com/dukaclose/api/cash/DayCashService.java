package com.dukaclose.api.cash;

import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import com.dukaclose.api.money.MoneyCategory;
import com.dukaclose.api.money.MoneyDirection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the drawer should hold, worked out from the saved records. Reversed entries never count.
 * Deliberately NOT exposed through any endpoint yet. It has no access check, and Phase 5 decides who may
 * see the expected figure (a cashier who sees it while counting could simply type it back).
 */
@Service
@RequiredArgsConstructor
public class DayCashService {

    private final JdbcTemplate jdbc;
    private final ShopDayRepository days;

    public record Summary(
            long openingFloatMinor,
            long cashSalesMinor, long pochiSalesMinor, long tillSalesMinor, long deniSalesMinor,
            long cashCollectionsMinor, long pochiCollectionsMinor, long tillCollectionsMinor,
            long otherCashInMinor,
            long cashMoneyOutMinor,
            long operatingExpensesMinor, long stockPurchasesMinor, long floatMovementsMinor,
            long expectedCashMinor) {

        public long totalSalesMinor() {
            return cashSalesMinor + pochiSalesMinor + tillSalesMinor + deniSalesMinor;
        }
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID shopDayId) {
        ShopDay day = days.findById(shopDayId).orElseThrow();

        // Sales count in full, by how they were paid. A reversed sale never happened.
        Map<String, Long> sales = byMethod("""
                SELECT a.method, SUM(a.amount_minor) FROM sale_allocations a
                  JOIN sales s ON s.id = a.sale_id
                 WHERE s.shop_day_id = ? AND s.status = 'COMPLETED' GROUP BY a.method""", shopDayId);

        // Repayments are collections of existing deni: money in, but never a sale
        Map<String, Long> collections = byMethod("""
                SELECT p.method, SUM(p.amount_minor) FROM debt_payments p
                 WHERE p.shop_day_id = ?
                   AND NOT EXISTS (SELECT 1 FROM debt_payment_reversals r WHERE r.payment_id = p.id)
                 GROUP BY p.method""", shopDayId);

        long otherCashIn = 0, cashOut = 0, operating = 0, stockPurchases = 0, floatMoves = 0;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT m.direction, m.category, m.method, SUM(m.amount_minor) AS total FROM money_movements m
                 WHERE m.shop_day_id = ?
                   AND NOT EXISTS (SELECT 1 FROM money_movement_reversals r WHERE r.movement_id = m.id)
                 GROUP BY m.direction, m.category, m.method""", shopDayId)) {
            long amount = ((Number) row.get("total")).longValue();
            MoneyDirection direction = MoneyDirection.valueOf((String) row.get("direction"));
            MoneyCategory category = MoneyCategory.valueOf((String) row.get("category"));
            boolean cash = "CASH".equals(row.get("method"));

            if (direction == MoneyDirection.IN) {
                if (cash) otherCashIn += amount;            // the database only allows cash here
            } else {
                if (cash) cashOut += amount;                // only cash leaving the drawer lowers it
                if (category.operatingExpense()) operating += amount;       // any method: it still cost money
                if (category == MoneyCategory.STOCK_PURCHASE) stockPurchases += amount;
                if (category == MoneyCategory.FLOAT_MOVEMENT) floatMoves += amount;
            }
        }

        long cashSales = sales.getOrDefault("CASH", 0L);
        long cashCollections = collections.getOrDefault("CASH", 0L);
        long expected = day.getOpeningFloatMinor() + cashSales + cashCollections + otherCashIn - cashOut;

        return new Summary(day.getOpeningFloatMinor(),
                cashSales, sales.getOrDefault("POCHI", 0L), sales.getOrDefault("TILL", 0L), sales.getOrDefault("DENI", 0L),
                cashCollections, collections.getOrDefault("POCHI", 0L), collections.getOrDefault("TILL", 0L),
                otherCashIn, cashOut, operating, stockPurchases, floatMoves, expected);
    }

    private Map<String, Long> byMethod(String sql, UUID dayId) {
        Map<String, Long> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        }, dayId);
        return result;
    }
}