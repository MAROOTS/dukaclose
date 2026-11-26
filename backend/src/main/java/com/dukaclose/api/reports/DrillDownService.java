// DrillDownService.java
package com.dukaclose.api.reports;

import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.inventory.AdjustmentReason;
import com.dukaclose.api.inventory.Unit;
import com.dukaclose.api.money.MoneyCategory;
import com.dukaclose.api.reports.DrillDownRows.*;
import com.dukaclose.api.reports.ReportScope.Paging;
import com.dukaclose.api.reports.ReportScope.Scope;
import com.dukaclose.api.sales.SaleStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DrillDownService {

    private static final List<String> EXPENSES = Arrays.stream(MoneyCategory.values())
            .filter(MoneyCategory::operatingExpense).map(Enum::name).toList();

    private final ReportScope scope;
    private final JdbcTemplate jdbc;

    /** Counts the filtered rows and sums the one figure the matching dashboard number is built from. */
    private <T> ReportPage<T> page(Scope s, Paging p, String summaryExpr, String rowSelect, String from,
                                   List<Object> args, String order, RowMapper<T> mapper) {
        long[] c = jdbc.queryForObject("SELECT COUNT(*), " + summaryExpr + from,
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add(p.size());
        rowArgs.add(p.offset());
        List<T> rows = jdbc.query(rowSelect + from + order + " LIMIT ? OFFSET ?", mapper, rowArgs.toArray());
        return new ReportPage<>(rows, p.page(), p.size(), c[0], (int) ((c[0] + p.size() - 1) / p.size()), c[1],
                s.from(), s.to());
    }

    private static Instant at(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    // ------------------------------------------------------------------------ sales

    @Transactional(readOnly = true)
    public ReportPage<SaleRow> sales(UUID userId, UUID businessId, UUID shopId, LocalDate from, LocalDate to,
                                     String status, int page, int size) {
        Scope s = scope.resolve(userId, businessId, shopId, from, to);
        Paging p = Paging.of(page, size);
        SaleStatus filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = SaleStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_STATUS", "The status must be COMPLETED or REVERSED.");
            }
        }
        String fromSql = " FROM sales s JOIN shop_days sd ON sd.id = s.shop_day_id JOIN shops sh ON sh.id = s.shop_id"
                + " JOIN users u ON u.id = s.cashier_id WHERE s.shop_id IN (" + s.in() + ")"
                + " AND sd.business_date BETWEEN ? AND ?" + (filter == null ? "" : " AND s.status = ?");
        List<Object> args = s.args();
        if (filter != null) args.add(filter.name());

        ReportPage<SaleRow> result = page(s, p, "COALESCE(SUM(s.total_minor) FILTER (WHERE s.status = 'COMPLETED'), 0)",
                "SELECT s.id, s.shop_id, sh.name AS shop_name, s.receipt_no, sd.business_date, s.created_at,"
                        + " u.display_name, s.total_minor, s.status",
                fromSql, args, " ORDER BY sd.business_date DESC, sh.name, s.receipt_no DESC",
                (rs, i) -> new SaleRow(rs.getObject("id", UUID.class), rs.getObject("shop_id", UUID.class),
                        rs.getString("shop_name"), rs.getLong("receipt_no"), rs.getObject("business_date", LocalDate.class),
                        at(rs, "created_at"), rs.getString("display_name"), rs.getLong("total_minor"),
                        rs.getString("status"), List.of()));

        if (result.items().isEmpty()) return result;
        Map<UUID, List<Payment>> payments = new HashMap<>();
        String in = String.join(",", Collections.nCopies(result.items().size(), "?"));
        jdbc.query("SELECT sale_id, method, amount_minor FROM sale_allocations WHERE sale_id IN (" + in
                + ") ORDER BY method", rs -> {
            payments.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                    .add(new Payment(rs.getString(2), rs.getLong(3)));
        }, result.items().stream().map(SaleRow::id).toArray());
        List<SaleRow> withPayments = result.items().stream().map(r -> new SaleRow(r.id(), r.shopId(), r.shopName(),
                r.receiptNo(), r.businessDate(), r.createdAt(), r.cashierName(), r.totalMinor(), r.status(),
                payments.getOrDefault(r.id(), List.of()))).toList();
        return new ReportPage<>(withPayments, result.page(), result.size(), result.totalElements(),
                result.totalPages(), result.summaryMinor(), result.from(), result.to());
    }

    // ------------------------------------------------------------------- money in and out

    /** summaryMinor adds up the entries that stand. Filter by expensesOnly or one category for a meaningful total. */
    @Transactional(readOnly = true)
    public ReportPage<MoneyRow> money(UUID userId, UUID businessId, UUID shopId, LocalDate from, LocalDate to,
                                      String category, boolean expensesOnly, int page, int size) {
        Scope s = scope.resolve(userId, businessId, shopId, from, to);
        Paging p = Paging.of(page, size);
        MoneyCategory filter = null;
        if (category != null && !category.isBlank()) {
            try {
                filter = MoneyCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_CATEGORY", "That money category does not exist.");
            }
        }
        StringBuilder fromSql = new StringBuilder(" FROM money_movements m JOIN shop_days sd ON sd.id = m.shop_day_id"
                + " JOIN shops sh ON sh.id = sd.shop_id JOIN users u ON u.id = m.created_by"
                + " LEFT JOIN money_movement_reversals mr ON mr.movement_id = m.id"
                + " WHERE sd.shop_id IN (" + s.in() + ") AND sd.business_date BETWEEN ? AND ?");
        List<Object> args = s.args();
        if (filter != null) {
            fromSql.append(" AND m.category = ?");
            args.add(filter.name());
        }
        if (expensesOnly) {
            fromSql.append(" AND m.direction = 'OUT' AND m.category IN (")
                    .append(String.join(",", Collections.nCopies(EXPENSES.size(), "?"))).append(")");
            args.addAll(EXPENSES);
        }
        return page(s, p, "COALESCE(SUM(m.amount_minor) FILTER (WHERE mr.id IS NULL), 0)",
                "SELECT m.id, sd.shop_id, sh.name AS shop_name, sd.business_date, m.created_at, m.direction,"
                        + " m.category, m.method, m.amount_minor, m.note, u.display_name, (mr.id IS NOT NULL) AS reversed",
                fromSql.toString(), args, " ORDER BY sd.business_date DESC, m.seq DESC", (rs, i) -> {
                    MoneyCategory c = MoneyCategory.valueOf(rs.getString("category"));
                    return new MoneyRow(rs.getObject("id", UUID.class), rs.getObject("shop_id", UUID.class),
                            rs.getString("shop_name"), rs.getObject("business_date", LocalDate.class), at(rs, "created_at"),
                            rs.getString("direction"), c.name(), rs.getString("method"), rs.getLong("amount_minor"),
                            rs.getString("note"), rs.getString("display_name"), rs.getBoolean("reversed"),
                            c.operatingExpense() && "OUT".equals(rs.getString("direction")));
                });
    }

    // ---------------------------------------------------------------- deni repayments

    @Transactional(readOnly = true)
    public ReportPage<CollectionRow> collections(UUID userId, UUID businessId, UUID shopId, LocalDate from,
                                                 LocalDate to, int page, int size) {
        Scope s = scope.resolve(userId, businessId, shopId, from, to);
        Paging p = Paging.of(page, size);
        String fromSql = " FROM debt_payments d JOIN shop_days sd ON sd.id = d.shop_day_id"
                + " JOIN shops sh ON sh.id = sd.shop_id JOIN customers c ON c.id = d.customer_id"
                + " JOIN users u ON u.id = d.received_by LEFT JOIN debt_payment_reversals pr ON pr.payment_id = d.id"
                + " WHERE sd.shop_id IN (" + s.in() + ") AND sd.business_date BETWEEN ? AND ?";
        return page(s, p, "COALESCE(SUM(d.amount_minor) FILTER (WHERE pr.id IS NULL), 0)",
                "SELECT d.id, sd.shop_id, sh.name AS shop_name, sd.business_date, d.created_at, c.id AS customer_id,"
                        + " c.name AS customer_name, d.method, d.amount_minor, u.display_name,"
                        + " (pr.id IS NOT NULL) AS reversed",
                fromSql, s.args(), " ORDER BY sd.business_date DESC, d.created_at DESC",
                (rs, i) -> new CollectionRow(rs.getObject("id", UUID.class), rs.getObject("shop_id", UUID.class),
                        rs.getString("shop_name"), rs.getObject("business_date", LocalDate.class), at(rs, "created_at"),
                        rs.getObject("customer_id", UUID.class), rs.getString("customer_name"), rs.getString("method"),
                        rs.getLong("amount_minor"), rs.getString("display_name"), rs.getBoolean("reversed")));
    }

    // ---------------------------------------------------------------------- refunds

    @Transactional(readOnly = true)
    public ReportPage<RefundRow> refunds(UUID userId, UUID businessId, UUID shopId, LocalDate from, LocalDate to,
                                         int page, int size) {
        Scope s = scope.resolve(userId, businessId, shopId, from, to);
        Paging p = Paging.of(page, size);
        String fromSql = " FROM sale_refunds r JOIN shop_days sd ON sd.id = r.shop_day_id"
                + " JOIN shop_days sdd ON sdd.id = r.sale_day_id JOIN sales sa ON sa.id = r.sale_id"
                + " JOIN shops sh ON sh.id = sd.shop_id JOIN users u ON u.id = r.refunded_by"
                + " WHERE sd.shop_id IN (" + s.in() + ") AND sd.business_date BETWEEN ? AND ?";
        return page(s, p, "COALESCE(SUM(r.total_minor), 0)",
                "SELECT r.id, sd.shop_id, sh.name AS shop_name, sd.business_date, r.created_at, r.sale_id,"
                        + " sa.receipt_no, sdd.business_date AS sale_date, r.total_minor, r.debt_credit_minor,"
                        + " r.reason, u.display_name",
                fromSql, s.args(), " ORDER BY sd.business_date DESC, r.created_at DESC",
                (rs, i) -> new RefundRow(rs.getObject("id", UUID.class), rs.getObject("shop_id", UUID.class),
                        rs.getString("shop_name"), rs.getObject("business_date", LocalDate.class), at(rs, "created_at"),
                        rs.getObject("sale_id", UUID.class), rs.getLong("receipt_no"),
                        rs.getObject("sale_date", LocalDate.class), rs.getLong("total_minor"),
                        rs.getLong("debt_credit_minor"), rs.getString("reason"), rs.getString("display_name")));
    }

    // -------------------------------------------------------------------- write-offs

    @Transactional(readOnly = true)
    public ReportPage<WriteOffRow> writeOffs(UUID userId, UUID businessId, UUID shopId, LocalDate from, LocalDate to,
                                             int page, int size) {
        Scope s = scope.resolve(userId, businessId, shopId, from, to);
        Paging p = Paging.of(page, size);
        String fromSql = " FROM debt_write_offs w JOIN customer_debts d ON d.id = w.debt_id"
                + " JOIN customers c ON c.id = w.customer_id JOIN sales sa ON sa.id = d.sale_id"
                + " JOIN shops sh ON sh.id = d.shop_id JOIN users u ON u.id = w.written_off_by"
                + " WHERE d.shop_id IN (" + s.in() + ") AND w.business_date BETWEEN ? AND ?";
        return page(s, p, "COALESCE(SUM(w.amount_minor), 0)",
                "SELECT w.id, d.shop_id, sh.name AS shop_name, w.business_date, w.created_at, c.id AS customer_id,"
                        + " c.name AS customer_name, d.sale_id, sa.receipt_no, w.amount_minor, w.reason, u.display_name",
                fromSql, s.args(), " ORDER BY w.business_date DESC, w.created_at DESC",
                (rs, i) -> new WriteOffRow(rs.getObject("id", UUID.class), rs.getObject("shop_id", UUID.class),
                        rs.getString("shop_name"), rs.getObject("business_date", LocalDate.class), at(rs, "created_at"),
                        rs.getObject("customer_id", UUID.class), rs.getString("customer_name"),
                        rs.getObject("sale_id", UUID.class), rs.getLong("receipt_no"), rs.getLong("amount_minor"),
                        rs.getString("reason"), rs.getString("display_name")));
    }

    // ------------------------------------------------------------------ stock adjustments

    /** summaryMinor is the shrinkage: damaged, expired and lost stock at cost, rounded per shop and day. */
    @Transactional(readOnly = true)
    public ReportPage<AdjustmentRow> adjustments(UUID userId, UUID businessId, UUID shopId, LocalDate from,
                                                 LocalDate to, String reason, int page, int size) {
        Scope s = scope.resolve(userId, businessId, shopId, from, to);
        Paging p = Paging.of(page, size);
        AdjustmentReason filter = null;
        if (reason != null && !reason.isBlank()) {
            try {
                filter = AdjustmentReason.valueOf(reason.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_REASON", "That adjustment reason does not exist.");
            }
        }
        String fromSql = " FROM stock_movements m JOIN shop_days sd ON sd.id = m.shop_day_id"
                + " JOIN shops sh ON sh.id = sd.shop_id JOIN items i ON i.id = m.item_id"
                + " JOIN users u ON u.id = m.created_by"
                + " WHERE m.movement_type = 'ADJUSTMENT' AND sd.shop_id IN (" + s.in() + ")"
                + " AND sd.business_date BETWEEN ? AND ?" + (filter == null ? "" : " AND m.reason = ?");
        List<Object> args = s.args();
        if (filter != null) args.add(filter.name());

        ReportPage<AdjustmentRow> result = page(s, p, "0",
                "SELECT m.id, sd.shop_id, sh.name AS shop_name, m.item_id, i.name AS item_name, i.unit,"
                        + " sd.business_date, m.created_at, m.quantity_delta, m.reason, m.note, u.display_name,"
                        + " ROUND(ABS(m.quantity_delta) * m.unit_cost)::bigint AS value_minor",
                fromSql, args, " ORDER BY sd.business_date DESC, m.seq DESC", (rs, i) -> {
                    String why = rs.getString("reason");
                    return new AdjustmentRow(rs.getObject("id", UUID.class), rs.getObject("shop_id", UUID.class),
                            rs.getString("shop_name"), rs.getObject("item_id", UUID.class), rs.getString("item_name"),
                            Unit.valueOf(rs.getString("unit")), rs.getObject("business_date", LocalDate.class),
                            at(rs, "created_at"), rs.getBigDecimal("quantity_delta"), why, rs.getString("note"),
                            rs.getString("display_name"), rs.getLong("value_minor"),
                            Set.of("DAMAGED", "EXPIRED", "LOST").contains(why));
                });

        // Rounded per shop and day, exactly like the dashboard, so the two figures can never disagree
        List<Object> shrinkArgs = new ArrayList<>(args);
        Long shrinkage = jdbc.queryForObject("SELECT COALESCE(SUM(ROUND(g.v)), 0)::bigint FROM (SELECT SUM(ABS(m.quantity_delta)"
                + " * m.unit_cost) AS v" + fromSql + " AND m.reason IN ('DAMAGED', 'EXPIRED', 'LOST')"
                + " GROUP BY sd.shop_id, sd.business_date) g", Long.class, shrinkArgs.toArray());
        return new ReportPage<>(result.items(), result.page(), result.size(), result.totalElements(),
                result.totalPages(), shrinkage == null ? 0 : shrinkage, result.from(), result.to());
    }
}