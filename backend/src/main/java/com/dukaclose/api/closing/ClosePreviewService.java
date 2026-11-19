package com.dukaclose.api.closing;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.cash.DayCashService;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.Hashing;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import com.dukaclose.api.deni.DebtLedgerService;
import com.dukaclose.api.inventory.AdjustmentReason;
import com.dukaclose.api.inventory.Unit;
import com.dukaclose.api.money.MoneyCategory;
import com.dukaclose.api.money.MoneyDirection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClosePreviewService {

    private final AccessService access;
    private final ShopDayRepository days;
    private final DayCashService cash;
    private final DebtLedgerService ledger;
    private final CashCountRepository counts;
    private final UserRepository users;
    private final BusinessClock clock;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public ClosePreview preview(UUID userId, UUID shopId) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        ShopDay day = days.findByShopIdAndStatus(shopId, DayStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("NO_OPEN_DAY", "There is no open day to close."));
        return build(ctx.shop(), day, ctx.role() == Role.OWNER);
    }

    /** Package-private: the close (Step 20) builds the owner's full version to store as the snapshot. */
    ClosePreview build(Shop shop, ShopDay day, boolean owner) {
        UUID dayId = day.getId();
        DayCashService.Summary c = cash.summary(dayId);
        String token = activityToken(dayId);

        // ---- sales ----
        long[] sale = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE status = 'COMPLETED'),
                       COUNT(*) FILTER (WHERE status = 'REVERSED'),
                       COALESCE(SUM(total_minor) FILTER (WHERE status = 'REVERSED'), 0)
                  FROM sales WHERE shop_day_id = ?""",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, dayId);
        ClosePreview.Sales sales = new ClosePreview.Sales(sale[0], c.totalSalesMinor(), c.cashSalesMinor(),
                c.pochiSalesMinor(), c.tillSalesMinor(), c.deniSalesMinor(), sale[1], sale[2]);

        // ---- repayments collected today ----
        long collectionCount = one("""
                SELECT COUNT(*) FROM debt_payments p WHERE p.shop_day_id = ?
                   AND NOT EXISTS (SELECT 1 FROM debt_payment_reversals r WHERE r.payment_id = p.id)""", dayId);
        long reversedCollections = one("SELECT COUNT(*) FROM debt_payment_reversals WHERE shop_day_id = ?", dayId);
        long collectedTotal = c.cashCollectionsMinor() + c.pochiCollectionsMinor() + c.tillCollectionsMinor();
        ClosePreview.CollectionsBlock collections = new ClosePreview.CollectionsBlock(collectionCount,
                c.cashCollectionsMinor(), c.pochiCollectionsMinor(), c.tillCollectionsMinor(),
                collectedTotal, reversedCollections);

        // ---- money in and out ----
        List<ClosePreview.CategoryTotal> byCategory = jdbc.query("""
                SELECT m.category, m.direction, SUM(m.amount_minor) FROM money_movements m
                 WHERE m.shop_day_id = ?
                   AND NOT EXISTS (SELECT 1 FROM money_movement_reversals r WHERE r.movement_id = m.id)
                 GROUP BY m.category, m.direction ORDER BY m.direction, m.category""",
                (rs, i) -> {
                    MoneyCategory category = MoneyCategory.valueOf(rs.getString(1));
                    return new ClosePreview.CategoryTotal(category, MoneyDirection.valueOf(rs.getString(2)),
                            rs.getLong(3), category.operatingExpense());
                }, dayId);
        long reversedMoney = one("SELECT COUNT(*) FROM money_movement_reversals WHERE shop_day_id = ?", dayId);
        ClosePreview.MoneyBlock money = new ClosePreview.MoneyBlock(c.cashMoneyOutMinor(),
                c.operatingExpensesMinor(), c.stockPurchasesMinor(), c.floatMovementsMinor(),
                c.otherCashInMinor(), reversedMoney, byCategory);

        // ---- deni: today's movement here, and the whole business's balances ----
        long newDeniCount = one("""
                SELECT COUNT(*) FROM customer_debts d JOIN sales s ON s.id = d.sale_id
                 WHERE s.shop_day_id = ? AND d.status = 'ACTIVE'""", dayId);
        DebtLedgerService.Totals owed = ledger.totals(shop.getBusinessId());
        ClosePreview.DeniBlock deni = new ClosePreview.DeniBlock(c.deniSalesMinor(), newDeniCount, collectedTotal,
                owed.outstandingMinor(), owed.overdueMinor(), owed.customerCount());

        // ---- stock ----
        List<ClosePreview.ItemSold> itemsSold = jdbc.query("""
                SELECT i.name, i.unit, SUM(l.quantity) AS qty, SUM(l.line_total_minor) AS total
                  FROM sale_lines l JOIN sales s ON s.id = l.sale_id JOIN items i ON i.id = l.item_id
                 WHERE s.shop_day_id = ? AND s.status = 'COMPLETED'
                 GROUP BY i.id, i.name, i.unit ORDER BY total DESC, i.name LIMIT 50""",
                (rs, i) -> new ClosePreview.ItemSold(rs.getString("name"), Unit.valueOf(rs.getString("unit")),
                        rs.getBigDecimal("qty"), rs.getLong("total")), dayId);
        List<ClosePreview.LowStock> lowStock = jdbc.query("""
                SELECT i.name, i.unit, si.on_hand, si.reorder_level
                  FROM shop_inventory si JOIN items i ON i.id = si.item_id
                 WHERE si.shop_id = ? AND i.status = 'ACTIVE' AND si.on_hand <= si.reorder_level
                 ORDER BY i.name""",
                (rs, i) -> new ClosePreview.LowStock(rs.getString("name"), Unit.valueOf(rs.getString("unit")),
                        rs.getBigDecimal("on_hand"), rs.getBigDecimal("reorder_level")), shop.getId());
        List<ClosePreview.AdjustmentLine> adjustments = jdbc.query("""
                SELECT i.name, i.unit, m.quantity_delta, m.reason, m.note, u.display_name, m.created_at
                  FROM stock_movements m JOIN items i ON i.id = m.item_id JOIN users u ON u.id = m.created_by
                 WHERE m.shop_day_id = ? AND m.movement_type = 'ADJUSTMENT'
                 ORDER BY m.seq DESC LIMIT 50""",
                (rs, i) -> new ClosePreview.AdjustmentLine(rs.getString("name"), Unit.valueOf(rs.getString("unit")),
                        rs.getBigDecimal("quantity_delta"), AdjustmentReason.valueOf(rs.getString("reason")),
                        rs.getString("note"), rs.getString("display_name"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()), dayId);
        ClosePreview.StockBlock stock = new ClosePreview.StockBlock(itemsSold, lowStock, adjustments);

        // ---- the drawer, and who may see it ----
        List<CashCount> all = counts.findByShopDayIdOrderByCreatedAtAsc(dayId);
        CashCount latest = all.isEmpty() ? null : all.get(all.size() - 1);
        boolean current = latest != null && latest.getActivityToken().equals(token);

        ClosePreview.Drawer drawer = null;
        if (owner || current) {
            drawer = new ClosePreview.Drawer(c.openingFloatMinor(), c.cashSalesMinor(), c.cashCollectionsMinor(),
                    c.otherCashInMinor(), c.cashMoneyOutMinor(), c.expectedCashMinor(),
                    latest == null ? null : latest.getCountedMinor(),
                    current ? latest.getCountedMinor() - c.expectedCashMinor() : null,
                    all.size(), current,
                    day.getCarriedOverMinor(),
                    day.getCarriedOverMinor() == null ? null : c.openingFloatMinor() - day.getCarriedOverMinor());
        }

        // ---- owner-only figures ----
        ClosePreview.Profit profit = null;
        List<ClosePreview.CountLine> countLines = null;
        if (owner) {
            long cogs = one("""
                    SELECT COALESCE(SUM(l.line_cost_minor), 0) FROM sale_lines l JOIN sales s ON s.id = l.sale_id
                     WHERE s.shop_day_id = ? AND s.status = 'COMPLETED'""", dayId);
            BigDecimal shrink = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(ABS(m.quantity_delta) * m.unit_cost), 0) FROM stock_movements m
                     WHERE m.shop_day_id = ? AND m.movement_type = 'ADJUSTMENT'
                       AND m.reason IN ('DAMAGED', 'EXPIRED', 'LOST')""", BigDecimal.class, dayId);
            long writeOffs = one("""
                    SELECT COALESCE(SUM(w.amount_minor), 0) FROM debt_write_offs w
                      JOIN customer_debts d ON d.id = w.debt_id
                     WHERE d.shop_id = ? AND w.business_date = ?""", shop.getId(), day.getBusinessDate());

            long net = c.totalSalesMinor();
            long gross = net - cogs;
            profit = new ClosePreview.Profit(net, cogs, gross, c.operatingExpensesMinor(),
                    gross - c.operatingExpensesMinor(),
                    shrink.setScale(0, RoundingMode.HALF_UP).longValueExact(), writeOffs);

            Map<UUID, String> names = users.findAllById(
                            all.stream().map(CashCount::getCountedBy).distinct().toList()).stream()
                    .collect(Collectors.toMap(User::getId, User::getDisplayName));
            countLines = new ArrayList<>();
            int number = 0;
            for (CashCount k : all) {
                number++;
                countLines.add(new ClosePreview.CountLine(number, k.getCountedMinor(), k.getExpectedMinor(),
                        k.getCountedMinor() - k.getExpectedMinor(), names.getOrDefault(k.getCountedBy(), ""),
                        k.getCreatedAt(), k.getActivityToken().equals(token)));
            }
        }

        String openedBy = users.findById(day.getOpenedBy()).map(User::getDisplayName).orElse("");
        return new ClosePreview(shop.getId(), shop.getName(), dayId, day.getBusinessDate(),
                day.getBusinessDate().isBefore(clock.today()), day.getCreatedAt(), openedBy, token,
                sales, collections, money, deni, stock, drawer, profit, countLines);
    }

    /**
     * A fingerprint of everything that moves the drawer or the day's sales: sales, repayments and money entries,
     * and the reversals of each. All of those tables only ever grow, so any change alters a count or a sum.
     * Stock deliveries and adjustments are not in it: they never touch the drawer.
     */
    public String activityToken(UUID dayId) {
        String joined = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM sales WHERE shop_day_id = ?)
                    || ',' || (SELECT COALESCE(SUM(total_minor), 0) FROM sales WHERE shop_day_id = ?)
                    || ',' || (SELECT COUNT(*) FROM sale_reversals WHERE shop_day_id = ?)
                    || ',' || (SELECT COUNT(*) FROM debt_payments WHERE shop_day_id = ?)
                    || ',' || (SELECT COALESCE(SUM(amount_minor), 0) FROM debt_payments WHERE shop_day_id = ?)
                    || ',' || (SELECT COUNT(*) FROM debt_payment_reversals WHERE shop_day_id = ?)
                    || ',' || (SELECT COUNT(*) FROM money_movements WHERE shop_day_id = ?)
                    || ',' || (SELECT COALESCE(SUM(amount_minor), 0) FROM money_movements WHERE shop_day_id = ?)
                    || ',' || (SELECT COUNT(*) FROM money_movement_reversals WHERE shop_day_id = ?)""",
                String.class, Collections.nCopies(9, dayId).toArray());
        return Hashing.sha256Hex(dayId + "|" + joined);
    }

    private long one(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}