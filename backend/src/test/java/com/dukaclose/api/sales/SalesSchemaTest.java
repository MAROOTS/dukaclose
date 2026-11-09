package com.dukaclose.api.sales;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.*;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import com.dukaclose.api.inventory.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesSchemaTest {

    @Autowired BusinessRepository businesses;
    @Autowired ShopRepository shops;
    @Autowired UserRepository users;
    @Autowired ShopDayRepository days;
    @Autowired ItemRepository items;
    @Autowired ShopInventoryRepository inventory;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    UUID bizId, shopId, dayId, userId, itemId;

    @BeforeEach
    void world() {
        Business biz = businesses.save(new Business("Schema"));
        Shop shop = shops.save(new Shop(biz.getId(), "Main"));
        User user = users.save(new User("Tester", "+254700000801", "hash"));
        ShopDay day = days.save(new ShopDay(shop.getId(), biz.getId(), LocalDate.of(2026, 1, 5), 0, user.getId()));
        Item item = items.save(new Item(biz.getId(), "Sugar", Unit.KG, null, null));
        em.flush();
        bizId = biz.getId();
        shopId = shop.getId();
        dayId = day.getId();
        userId = user.getId();
        itemId = item.getId();
    }

    private UUID insertSale(long total) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into sales (id, business_id, shop_id, shop_day_id, receipt_no, cashier_id, total_minor, "
                        + "idempotency_key, request_hash) values (?,?,?,?,?,?,?,?,?)",
                id, bizId, shopId, dayId, 1L, userId, total, UUID.randomUUID(), "hash");
        return id;
    }

    private void insertLine(UUID saleId, long lineTotal) {
        jdbc.update("insert into sale_lines (sale_id, shop_id, business_id, item_id, line_no, quantity, "
                        + "unit_price_minor, unit_cost, line_total_minor, line_cost_minor) values (?,?,?,?,?,?,?,?,?,?)",
                saleId, shopId, bizId, itemId, 1, new BigDecimal("1"), lineTotal, new BigDecimal("0"), lineTotal, 0L);
    }

    private void insertPayment(UUID saleId, String method, long amount) {
        jdbc.update("insert into sale_allocations (sale_id, shop_id, method, amount_minor) values (?,?,?,?)",
                saleId, shopId, method, amount);
    }

    /** Deferred checks normally run at commit. Tests never commit, so run them now. */
    private void runCommitChecks() {
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
    }

    @Test
    void aBalancedSaleIsAccepted() {
        UUID sale = insertSale(10000);
        insertLine(sale, 10000);
        insertPayment(sale, "CASH", 6000);
        insertPayment(sale, "TILL", 4000);

        assertThatCode(this::runCommitChecks).doesNotThrowAnyException();
    }

    @Test
    void paymentsThatDoNotAddUpAreRefused() {
        UUID sale = insertSale(10000);
        insertLine(sale, 10000);
        insertPayment(sale, "CASH", 9000);

        assertThatThrownBy(this::runCommitChecks).hasMessageContaining("Payment allocations");
    }

    @Test
    void aSaleWithoutPaymentsIsRefused() {
        UUID sale = insertSale(10000);
        insertLine(sale, 10000);

        assertThatThrownBy(this::runCommitChecks).hasMessageContaining("Payment allocations");
    }

    @Test
    void linesThatDoNotAddUpAreRefused() {
        UUID sale = insertSale(10000);
        insertLine(sale, 9000);
        insertPayment(sale, "CASH", 10000);

        assertThatThrownBy(this::runCommitChecks).hasMessageContaining("Sale lines");
    }

    @Test
    void aSaleWithoutLinesIsRefused() {
        UUID sale = insertSale(10000);
        insertPayment(sale, "CASH", 10000);

        assertThatThrownBy(this::runCommitChecks).hasMessageContaining("at least one line");
    }

    @Test
    void aSaleCannotBeRewritten() {
        UUID sale = insertSale(10000);

        assertThatThrownBy(() -> jdbc.update("update sales set total_minor = 1 where id = ?", sale))
                .hasMessageContaining("append-only");
    }

    @Test
    void aSaleCannotBeDeleted() {
        UUID sale = insertSale(10000);

        assertThatThrownBy(() -> jdbc.update("delete from sales where id = ?", sale))
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void aSaleCanBeMarkedReversedButNeverRestored() {
        UUID sale = insertSale(10000);
        jdbc.update("update sales set status = 'REVERSED' where id = ?", sale);          // allowed

        assertThatThrownBy(() -> jdbc.update("update sales set status = 'COMPLETED' where id = ?", sale))
                .hasMessageContaining("cannot be restored");
    }

    @Test
    void linesCannotBeEdited() {
        UUID sale = insertSale(10000);
        insertLine(sale, 10000);

        assertThatThrownBy(() -> jdbc.update("update sale_lines set quantity = 2 where sale_id = ?", sale))
                .hasMessageContaining("append-only");
    }

    @Test
    void paymentsCannotBeDeleted() {
        UUID sale = insertSale(10000);
        insertPayment(sale, "CASH", 10000);

        assertThatThrownBy(() -> jdbc.update("delete from sale_allocations where sale_id = ?", sale))
                .hasMessageContaining("append-only");
    }

    @Test
    void aSaleMovementMustPointAtASale() {
        inventory.saveAndFlush(new ShopInventory(shopId, bizId, itemId, null));

        assertThatThrownBy(() -> jdbc.update(
                "insert into stock_movements (business_id, shop_id, item_id, request_id, movement_type, "
                        + "quantity_delta, balance_after, created_by) values (?,?,?,?,?,?,?,?)",
                bizId, shopId, itemId, UUID.randomUUID(), "SALE", new BigDecimal("-1"), BigDecimal.ZERO, userId))
                .hasMessageContaining("ck_smv_sale_ref");
    }
}