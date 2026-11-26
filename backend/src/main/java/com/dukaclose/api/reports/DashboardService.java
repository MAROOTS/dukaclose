// DashboardService.java
package com.dukaclose.api.reports;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.business.ShopRepository;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.deni.DebtLedgerService;
import com.dukaclose.api.money.MoneyCategory;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private enum Period {
        TODAY, WEEK, MONTH;

        static Period parse(String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw ApiException.badRequest("INVALID_PERIOD", "The period must be today, week or month.");
            }
        }
    }

    private record Range(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {}

    private record Key(UUID shopId, LocalDate date) {}

    /** One shop on one business date. Everything the dashboard shows is a sum of these. */
    private static final class Cell {
        long salesCount, sales, cash, pochi, till, deni;
        long refundsCount, refunds;
        long collCash, collPochi, collTill;
        long operatingExpenses, soldCost, restockedCost, writeOffs, shrinkage;

        void add(Cell o) {
            salesCount += o.salesCount; sales += o.sales; cash += o.cash; pochi += o.pochi; till += o.till; deni += o.deni;
            refundsCount += o.refundsCount; refunds += o.refunds;
            collCash += o.collCash; collPochi += o.collPochi; collTill += o.collTill;
            operatingExpenses += o.operatingExpenses; soldCost += o.soldCost; restockedCost += o.restockedCost;
            writeOffs += o.writeOffs; shrinkage += o.shrinkage;
        }
    }

    private final AccessService access;
    private final ShopRepository shops;
    private final DebtLedgerService ledger;
    private final BusinessClock clock;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID userId, UUID businessId, String periodText, UUID shopId, LocalDate asOfParam) {
        access.requireBusinessOwner(userId, businessId);
        Period period = Period.parse(periodText);

        LocalDate today = clock.today();
        LocalDate asOf = asOfParam == null ? today : asOfParam;
        if (asOf.isAfter(today)) {
            throw ApiException.badRequest("INVALID_DATE", "That date is in the future.");
        }

        // The owner of this business may look at any of its shops. Anything else looks the same as "not yours".
        List<Shop> all = shops.findByBusinessId(businessId).stream()
                .sorted(Comparator.comparing(Shop::getName)).toList();
        List<Shop> selected = shopId == null ? all
                : all.stream().filter(s -> s.getId().equals(shopId)).toList();
        if (shopId != null && selected.isEmpty()) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this resource");
        }

        Range range = range(period, asOf);
        List<UUID> ids = selected.stream().map(Shop::getId).toList();
        Map<Key, Cell> now = ids.isEmpty() ? Map.of() : load(ids, range.from(), range.to());
        Map<Key, Cell> before = ids.isEmpty() ? Map.of() : load(ids, range.previousFrom(), range.previousTo());

        List<DashboardResponse.ShopRow> byShop = new ArrayList<>();
        for (Shop shop : selected) {
            Cell sum = new Cell();
            now.forEach((k, c) -> {
                if (k.shopId().equals(shop.getId())) sum.add(c);
            });
            byShop.add(new DashboardResponse.ShopRow(shop.getId(), shop.getName(), totals(sum)));
        }

        List<DashboardResponse.DayRow> byDay = new ArrayList<>();
        for (LocalDate d = range.from(); !d.isAfter(range.to()); d = d.plusDays(1)) {
            Cell sum = new Cell();
            for (Map.Entry<Key, Cell> e : now.entrySet()) {
                if (e.getKey().date().equals(d)) sum.add(e.getValue());
            }
            byDay.add(new DashboardResponse.DayRow(d, totals(sum)));
        }

        DebtLedgerService.Totals owed = ledger.totals(businessId);
        return new DashboardResponse(period.name().toLowerCase(Locale.ROOT), asOf, range.from(), range.to(),
                range.previousFrom(), range.previousTo(),
                selected.stream().map(s -> new DashboardResponse.ShopRef(s.getId(), s.getName())).toList(),
                totals(sum(now.values())), totals(sum(before.values())), byShop, byDay,
                new DashboardResponse.DeniNow(owed.outstandingMinor(), owed.overdueMinor(), owed.customerCount()));
    }

    // ------------------------------------------------------------------- periods

    /** Weeks run Sunday to Saturday. Week and month are "so far": from the start of the period up to asOf. */
    private Range range(Period period, LocalDate asOf) {
        switch (period) {
            case WEEK: {
                LocalDate from = asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
                long into = ChronoUnit.DAYS.between(from, asOf);
                LocalDate previousFrom = from.minusWeeks(1);
                return new Range(from, asOf, previousFrom, previousFrom.plusDays(into));
            }
            case MONTH: {
                LocalDate from = asOf.withDayOfMonth(1);
                LocalDate previousFrom = from.minusMonths(1);
                LocalDate lastOfPrevious = previousFrom.withDayOfMonth(previousFrom.lengthOfMonth());
                LocalDate sameDay = previousFrom.plusDays(asOf.getDayOfMonth() - 1L);
                return new Range(from, asOf, previousFrom, sameDay.isAfter(lastOfPrevious) ? lastOfPrevious : sameDay);
            }
            default:
                return new Range(asOf, asOf, asOf.minusDays(1), asOf.minusDays(1));
        }
    }

    // ------------------------------------------------------------------- the queries

    /**
     * One query per measure, each grouped by shop and business date. The definitions are the close's definitions:
     * reversed sales and reversed repayments or entries never count, and each measure is dated by its own day.
     */
    private Map<Key, Cell> load(List<UUID> ids, LocalDate from, LocalDate to) {
        Map<Key, Cell> cells = new HashMap<>();
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] args = args(ids, from, to);

        // sales that stand
        jdbc.query("""
                SELECT sd.shop_id, sd.business_date, COUNT(*), COALESCE(SUM(s.total_minor), 0)
                  FROM sales s JOIN shop_days sd ON sd.id = s.shop_day_id
                 WHERE s.status = 'COMPLETED' AND sd.shop_id IN (%s) AND sd.business_date BETWEEN ? AND ?
                 GROUP BY sd.shop_id, sd.business_date""".formatted(in), rs -> {
            Cell c = cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class));
            c.salesCount = rs.getLong(3);
            c.sales = rs.getLong(4);
        }, args);

        // how they were paid
        jdbc.query("""
                SELECT sd.shop_id, sd.business_date, a.method, SUM(a.amount_minor)
                  FROM sale_allocations a JOIN sales s ON s.id = a.sale_id JOIN shop_days sd ON sd.id = s.shop_day_id
                 WHERE s.status = 'COMPLETED' AND sd.shop_id IN (%s) AND sd.business_date BETWEEN ? AND ?
                 GROUP BY sd.shop_id, sd.business_date, a.method""".formatted(in), rs -> {
            Cell c = cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class));
            long amount = rs.getLong(4);
            switch (rs.getString(3)) {
                case "CASH" -> c.cash = amount;
                case "POCHI" -> c.pochi = amount;
                case "TILL" -> c.till = amount;
                case "DENI" -> c.deni = amount;
                default -> throw new IllegalStateException("Unknown payment method " + rs.getString(3));
            }
        }, args);

        // deni repayments, dated by the day they were collected
        jdbc.query("""
                SELECT sd.shop_id, sd.business_date, p.method, SUM(p.amount_minor)
                  FROM debt_payments p JOIN shop_days sd ON sd.id = p.shop_day_id
                 WHERE sd.shop_id IN (%s) AND sd.business_date BETWEEN ? AND ?
                   AND NOT EXISTS (SELECT 1 FROM debt_payment_reversals r WHERE r.payment_id = p.id)
                 GROUP BY sd.shop_id, sd.business_date, p.method""".formatted(in), rs -> {
            Cell c = cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class));
            long amount = rs.getLong(4);
            switch (rs.getString(3)) {
                case "CASH" -> c.collCash = amount;
                case "POCHI" -> c.collPochi = amount;
                case "TILL" -> c.collTill = amount;
                default -> throw new IllegalStateException("Unknown collection method " + rs.getString(3));
            }
        }, args);

        // refunds, dated by the day they were recorded (never the day of the original sale)
        jdbc.query("""
                SELECT sd.shop_id, sd.business_date, COUNT(*), COALESCE(SUM(r.total_minor), 0)
                  FROM sale_refunds r JOIN shop_days sd ON sd.id = r.shop_day_id
                 WHERE sd.shop_id IN (%s) AND sd.business_date BETWEEN ? AND ?
                 GROUP BY sd.shop_id, sd.business_date""".formatted(in), rs -> {
            Cell c = cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class));
            c.refundsCount = rs.getLong(3);
            c.refunds = rs.getLong(4);
        }, args);

        // operating expenses only: stock purchases and cash taken out of the drawer are not expenses
        List<String> expenseCategories = Arrays.stream(MoneyCategory.values())
                .filter(MoneyCategory::operatingExpense).map(Enum::name).toList();
        Object[] expenseArgs = new Object[args.length + expenseCategories.size()];
        System.arraycopy(args, 0, expenseArgs, 0, args.length);
        for (int i = 0; i < expenseCategories.size(); i++) {
            expenseArgs[args.length + i] = expenseCategories.get(i);
        }
        jdbc.query("""
                SELECT sd.shop_id, sd.business_date, SUM(m.amount_minor)
                  FROM money_movements m JOIN shop_days sd ON sd.id = m.shop_day_id
                 WHERE sd.shop_id IN (%s) AND sd.business_date BETWEEN ? AND ?
                   AND m.direction = 'OUT' AND m.category IN (%s)
                   AND NOT EXISTS (SELECT 1 FROM money_movement_reversals r WHERE r.movement_id = m.id)
                 GROUP BY sd.shop_id, sd.business_date""".formatted(in,
                String.join(",", Collections.nCopies(expenseCategories.size(), "?"))), rs -> {
            cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class)).operatingExpenses = rs.getLong(3);
        }, expenseArgs);

        // what the goods cost
        jdbc.query("""
                SELECT sd.shop_id, sd.business_date, COALESCE(SUM(l.line_cost_minor), 0)
                  FROM sale_lines l JOIN sales s ON s.id = l.sale_id JOIN shop_days sd ON sd.id = s.shop_day_id
                 WHERE s.status = 'COMPLETED' AND sd.shop_id IN (%s) AND sd.business_date BETWEEN ? AND ?
                 GROUP BY sd.shop_id, sd.business_date""".formatted(in), rs -> {
            cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class)).soldCost = rs.getLong(3);
        }, args);

        // goods that came back onto the shelf give their cost back, on the refund's day
        jdbc.query("""
                SELECT sd.shop_id, sd.business_date, COALESCE(SUM(ROUND(l.quantity * l.unit_cost)), 0)::bigint
                  FROM sale_refund_lines l JOIN sale_refunds r ON r.id = l.refund_id
                  JOIN shop_days sd ON sd.id = r.shop_day_id
                 WHERE l.disposition = 'RESTOCK' AND sd.shop_id IN (%s) AND sd.business_date BETWEEN ? AND ?
                 GROUP BY sd.shop_id, sd.business_date""".formatted(in), rs -> {
            cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class)).restockedCost = rs.getLong(3);
        }, args);

        // deni written off, dated by the day the owner did it
        jdbc.query("""
                SELECT d.shop_id, w.business_date, SUM(w.amount_minor)
                  FROM debt_write_offs w JOIN customer_debts d ON d.id = w.debt_id
                 WHERE d.shop_id IN (%s) AND w.business_date BETWEEN ? AND ?
                 GROUP BY d.shop_id, w.business_date""".formatted(in), rs -> {
            cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class)).writeOffs = rs.getLong(3);
        }, args);

        // damaged, expired and lost stock at cost, rounded per shop and day exactly like the close does
        jdbc.query("""
                SELECT sd.shop_id, sd.business_date, COALESCE(SUM(ABS(m.quantity_delta) * m.unit_cost), 0)
                  FROM stock_movements m JOIN shop_days sd ON sd.id = m.shop_day_id
                 WHERE m.movement_type = 'ADJUSTMENT' AND m.reason IN ('DAMAGED', 'EXPIRED', 'LOST')
                   AND sd.shop_id IN (%s) AND sd.business_date BETWEEN ? AND ?
                 GROUP BY sd.shop_id, sd.business_date""".formatted(in), rs -> {
            cell(cells, rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class)).shrinkage =
                    rs.getBigDecimal(3).setScale(0, RoundingMode.HALF_UP).longValueExact();
        }, args);

        return cells;
    }

    // ----------------------------------------------------------------------- helpers

    private static Object[] args(List<UUID> ids, LocalDate from, LocalDate to) {
        List<Object> list = new ArrayList<>(ids);
        list.add(from);
        list.add(to);
        return list.toArray();
    }

    private static Cell cell(Map<Key, Cell> cells, UUID shopId, LocalDate date) {
        return cells.computeIfAbsent(new Key(shopId, date), k -> new Cell());
    }

    private static Cell sum(Collection<Cell> cells) {
        Cell total = new Cell();
        cells.forEach(total::add);
        return total;
    }

    private static DashboardResponse.Totals totals(Cell c) {
        long net = c.sales - c.refunds;
        long cogs = c.soldCost - c.restockedCost;
        long gross = net - cogs;
        return new DashboardResponse.Totals(c.salesCount, c.sales, c.cash, c.pochi, c.till, c.deni,
                c.refundsCount, c.refunds, net,
                c.collCash + c.collPochi + c.collTill, c.collCash, c.collPochi, c.collTill,
                c.operatingExpenses, cogs, gross, gross - c.operatingExpenses,
                c.writeOffs, c.shrinkage);
    }
}