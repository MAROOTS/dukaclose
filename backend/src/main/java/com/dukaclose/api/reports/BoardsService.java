// BoardsService.java
package com.dukaclose.api.reports;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.business.ShopRepository;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.RecordStatus;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.inventory.Unit;
import com.dukaclose.api.reports.OwnerBoards.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardsService {

    private static final RowMapper<CloseRow> CLOSE_ROW = (rs, i) -> {
        long difference = rs.getLong("difference_minor");
        return new CloseRow(rs.getObject("shop_id", UUID.class), rs.getString("shop_name"),
                rs.getObject("business_date", LocalDate.class), rs.getObject("closed_at", OffsetDateTime.class).toInstant(),
                rs.getString("closed_by_name"), rs.getLong("counted_minor"), rs.getLong("expected_minor"), difference,
                difference != 0, rs.getString("note"), rs.getLong("sales_total_minor"));
    };

    private static final String CLOSE_COLUMNS = """
            c.shop_id, sh.name AS shop_name, c.business_date, c.closed_at, u.display_name AS closed_by_name,
            c.counted_minor, c.expected_minor, c.difference_minor, c.note, c.sales_total_minor""";

    private static final String CLOSE_JOINS = """
            FROM day_closes c JOIN shops sh ON sh.id = c.shop_id JOIN users u ON u.id = c.closed_by""";

    /** What each debt still owes: original, less repayments that stand, write-offs and refund credits. */
    private static final String DEBT_BALANCES = """
            WITH d AS (
              SELECT d.customer_id, d.due_date,
                     d.original_amount_minor
                       - COALESCE((SELECT SUM(a.amount_minor) FROM debt_payment_allocations a
                                    WHERE a.debt_id = d.id
                                      AND NOT EXISTS (SELECT 1 FROM debt_payment_reversals r WHERE r.payment_id = a.payment_id)), 0)
                       - COALESCE((SELECT SUM(w.amount_minor) FROM debt_write_offs w WHERE w.debt_id = d.id), 0)
                       - COALESCE((SELECT SUM(c.amount_minor) FROM debt_credits c WHERE c.debt_id = d.id), 0) AS balance
                FROM customer_debts d
               WHERE d.business_id = ? AND d.status = 'ACTIVE')
            """;

    private final AccessService access;
    private final ShopRepository shops;
    private final BusinessClock clock;
    private final JdbcTemplate jdbc;

    // ------------------------------------------------------------------ the close board

    @Transactional(readOnly = true)
    public CloseBoard closeBoard(UUID userId, UUID businessId, int days) {
        access.requireBusinessOwner(userId, businessId);
        if (days < 1 || days > 90) {
            throw ApiException.badRequest("INVALID_DAYS", "Look back between 1 and 90 days.");
        }
        LocalDate today = clock.today();

        List<Shop> active = shops.findByBusinessId(businessId).stream()
                .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
                .sorted(Comparator.comparing(Shop::getName)).toList();

        record OpenDay(LocalDate date, long openingFloat, Long carriedOver, String openedBy) {}
        Map<UUID, OpenDay> open = new HashMap<>();
        jdbc.query("""
                SELECT d.shop_id, d.business_date, d.opening_float_minor, d.carried_over_minor, u.display_name
                  FROM shop_days d JOIN users u ON u.id = d.opened_by
                 WHERE d.business_id = ? AND d.status = 'OPEN'""", rs -> {
            long carried = rs.getLong(4);
            open.put(rs.getObject(1, UUID.class), new OpenDay(rs.getObject(2, LocalDate.class), rs.getLong(3),
                    rs.wasNull() ? null : carried, rs.getString(5)));
        }, businessId);

        Set<UUID> closedToday = new HashSet<>(jdbc.query(
                "SELECT shop_id FROM shop_days WHERE business_id = ? AND business_date = ? AND status = 'CLOSED'",
                (rs, i) -> rs.getObject(1, UUID.class), businessId, today));

        Map<UUID, CloseRow> last = new HashMap<>();
        jdbc.query("SELECT DISTINCT ON (c.shop_id) " + CLOSE_COLUMNS + " " + CLOSE_JOINS
                        + " WHERE c.business_id = ? ORDER BY c.shop_id, c.business_date DESC",
                CLOSE_ROW, businessId).forEach(r -> last.put(r.shopId(), r));

        List<CloseRow> recent = jdbc.query("SELECT " + CLOSE_COLUMNS + " " + CLOSE_JOINS
                        + " WHERE c.business_id = ? AND c.business_date >= ?"
                        + " ORDER BY c.business_date DESC, sh.name LIMIT 200",
                CLOSE_ROW, businessId, today.minusDays(days));

        List<ShopState> states = new ArrayList<>();
        long needsClose = 0;
        for (Shop shop : active) {
            OpenDay o = open.get(shop.getId());
            String state;
            if (o != null) {
                state = o.date().isBefore(today) ? "NEEDS_CLOSE" : "OPEN";
            } else {
                state = closedToday.contains(shop.getId()) ? "CLOSED" : "NOT_OPEN";
            }
            if (state.equals("NEEDS_CLOSE")) needsClose++;
            states.add(new ShopState(shop.getId(), shop.getName(), state,
                    o == null ? null : o.date(), o == null ? null : o.openedBy(),
                    o == null ? null : o.openingFloat(),
                    o == null || o.carriedOver() == null ? null : o.openingFloat() - o.carriedOver(),
                    last.get(shop.getId())));
        }
        long flagged = recent.stream().filter(CloseRow::flagged).count();
        return new CloseBoard(today, days, states, needsClose, flagged, recent);
    }

    // -------------------------------------------------------------------- deni ageing

    @Transactional(readOnly = true)
    public DeniAgeing deniAgeing(UUID userId, UUID businessId, int page, int size) {
        access.requireBusinessOwner(userId, businessId);
        LocalDate today = clock.today();
        ReportScope.Paging p = ReportScope.Paging.of(page, size);
        LocalDate d7 = today.minusDays(7);
        LocalDate d30 = today.minusDays(30);

        Buckets buckets = jdbc.queryForObject(DEBT_BALANCES + """
                SELECT COALESCE(SUM(balance) FILTER (WHERE due_date >= ?), 0),
                       COALESCE(SUM(balance) FILTER (WHERE due_date < ? AND due_date >= ?), 0),
                       COALESCE(SUM(balance) FILTER (WHERE due_date < ? AND due_date >= ?), 0),
                       COALESCE(SUM(balance) FILTER (WHERE due_date < ?), 0)
                  FROM d WHERE balance > 0""",
                (rs, i) -> new Buckets(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                businessId, today, today, d7, d7, d30, d30);

        long[] totals = jdbc.queryForObject(DEBT_BALANCES + """
                SELECT COALESCE(SUM(balance), 0), COALESCE(SUM(balance) FILTER (WHERE due_date < ?), 0),
                       COUNT(DISTINCT customer_id)
                  FROM d WHERE balance > 0""",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, businessId, today);

        List<AgeingRow> rows = jdbc.query(DEBT_BALANCES + """
                SELECT c.id, c.name, c.phone, c.credit_limit_minor, SUM(d.balance) AS outstanding,
                       COALESCE(SUM(d.balance) FILTER (WHERE d.due_date < ?), 0) AS overdue,
                       MIN(d.due_date) FILTER (WHERE d.due_date < ?) AS oldest_due,
                       MIN(d.due_date) AS earliest_due
                  FROM d JOIN customers c ON c.id = d.customer_id
                 WHERE d.balance > 0
                 GROUP BY c.id, c.name, c.phone, c.credit_limit_minor
                 ORDER BY oldest_due ASC NULLS LAST, outstanding DESC, c.name
                 LIMIT ? OFFSET ?""", (rs, i) -> {
            long limit = rs.getLong("credit_limit_minor");
            Long creditLimit = rs.wasNull() ? null : limit;
            long outstanding = rs.getLong("outstanding");
            LocalDate oldest = rs.getObject("oldest_due", LocalDate.class);
            return new AgeingRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("phone"),
                    outstanding, rs.getLong("overdue"),
                    oldest == null ? 0 : (int) ChronoUnit.DAYS.between(oldest, today),
                    rs.getObject("earliest_due", LocalDate.class), creditLimit,
                    creditLimit != null && outstanding > creditLimit, null, null);
        }, businessId, today, today, p.size(), p.offset());

        // each customer's latest repayment that still stands
        Map<UUID, Object[]> lastPayment = new HashMap<>();
        if (!rows.isEmpty()) {
            String in = String.join(",", Collections.nCopies(rows.size(), "?"));
            jdbc.query("""
                    SELECT DISTINCT ON (p.customer_id) p.customer_id, p.created_at, p.amount_minor
                      FROM debt_payments p
                     WHERE p.customer_id IN (%s)
                       AND NOT EXISTS (SELECT 1 FROM debt_payment_reversals r WHERE r.payment_id = p.id)
                     ORDER BY p.customer_id, p.created_at DESC""".formatted(in), rs -> {
                lastPayment.put(rs.getObject(1, UUID.class),
                        new Object[]{rs.getObject(2, OffsetDateTime.class).toInstant(), rs.getLong(3)});
            }, rows.stream().map(AgeingRow::customerId).toArray());
        }
        List<AgeingRow> items = rows.stream().map(r -> {
            Object[] lp = lastPayment.get(r.customerId());
            return new AgeingRow(r.customerId(), r.name(), r.phone(), r.outstandingMinor(), r.overdueMinor(),
                    r.oldestOverdueDays(), r.earliestDueDate(), r.creditLimitMinor(), r.overLimit(),
                    lp == null ? null : (Instant) lp[0], lp == null ? null : (Long) lp[1]);
        }).toList();

        return new DeniAgeing(today, buckets, totals[0], totals[1], totals[2], items, p.page(), p.size(),
                (int) ((totals[2] + p.size() - 1) / p.size()));
    }

    // ---------------------------------------------------------------------- low stock

    /** Same rule as the shop's stock list: active items at or below the shop's reorder level. */
    @Transactional(readOnly = true)
    public LowStockPage lowStock(UUID userId, UUID businessId, UUID shopId, int page, int size) {
        access.requireBusinessOwner(userId, businessId);
        ReportScope.Paging p = ReportScope.Paging.of(page, size);
        if (shopId != null && shops.findByBusinessId(businessId).stream().noneMatch(s -> s.getId().equals(shopId))) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this resource");
        }

        String where = """
                  FROM shop_inventory si JOIN items i ON i.id = si.item_id JOIN shops sh ON sh.id = si.shop_id
                 WHERE si.business_id = ? AND sh.status = 'ACTIVE' AND i.status = 'ACTIVE'
                   AND si.on_hand <= si.reorder_level""" + (shopId == null ? "" : " AND si.shop_id = ?");
        List<Object> args = new ArrayList<>(List.of(businessId));
        if (shopId != null) args.add(shopId);

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add(p.size());
        rowArgs.add(p.offset());
        List<LowStockRow> rows = jdbc.query("""
                SELECT si.shop_id, sh.name AS shop_name, si.item_id, i.name AS item_name, i.unit, si.on_hand,
                       si.reorder_level, si.price_minor,
                       (SELECT MAX(m.created_at) FROM stock_movements m
                         WHERE m.shop_id = si.shop_id AND m.item_id = si.item_id) AS last_movement""" + where
                + " ORDER BY sh.name, i.name LIMIT ? OFFSET ?", (rs, i) -> {
            long price = rs.getLong("price_minor");
            Long priceMinor = rs.wasNull() ? null : price;
            OffsetDateTime moved = rs.getObject("last_movement", OffsetDateTime.class);
            return new LowStockRow(rs.getObject("shop_id", UUID.class), rs.getString("shop_name"),
                    rs.getObject("item_id", UUID.class), rs.getString("item_name"),
                    Unit.valueOf(rs.getString("unit")), rs.getBigDecimal("on_hand"), rs.getBigDecimal("reorder_level"),
                    priceMinor, moved == null ? null : moved.toInstant());
        }, rowArgs.toArray());

        long count = total == null ? 0 : total;
        return new LowStockPage(rows, p.page(), p.size(), count, (int) ((count + p.size() - 1) / p.size()));
    }
}