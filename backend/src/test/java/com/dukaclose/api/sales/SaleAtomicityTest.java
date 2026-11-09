package com.dukaclose.api.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.*;
import com.dukaclose.api.day.OpenDayRequest;
import com.dukaclose.api.day.ShopDayService;
import com.dukaclose.api.inventory.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class SaleAtomicityTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @Autowired BusinessRepository businesses;
    @Autowired ShopRepository shops;
    @Autowired UserRepository users;
    @Autowired BusinessMembershipRepository businessMemberships;
    @Autowired ShopMembershipRepository shopMemberships;
    @Autowired ItemRepository items;
    @Autowired ShopInventoryRepository inventory;
    @Autowired StockMovementRepository movements;
    @Autowired SaleRepository sales;
    @Autowired SaleLineRepository saleLines;
    @Autowired StockService stockService;
    @Autowired InventoryService inventoryService;
    @Autowired ShopDayService dayService;
    @Autowired SaleService saleService;

    @BeforeEach
    void clean() {
        // This class really commits, so it must never run against anything but the test database
        assertThat(jdbc.queryForObject("select current_database()", String.class)).endsWith("_test");
        truncate();
    }

    @AfterEach
    void cleanAgain() {
        truncate();
    }

    private void truncate() {
        jdbc.execute("TRUNCATE sale_allocations, sale_lines, sales, stock_movements, shop_inventory, shop_days, "
                + "shop_counters, items, shop_memberships, business_memberships, shops, users, businesses CASCADE");
    }

    @Test
    void everythingASaleWritesIsRolledBackTogether() {
        Business biz = businesses.save(new Business("Atomic"));
        Shop shop = shops.save(new Shop(biz.getId(), "Main"));
        User owner = users.save(new User("Owner", "+254700000701", "hash"));
        User cashier = users.save(new User("Cashier", "+254700000702", "hash"));
        businessMemberships.save(new BusinessMembership(biz.getId(), owner.getId(), Role.OWNER));
        shopMemberships.save(new ShopMembership(shop.getId(), cashier.getId(), Role.CASHIER));
        Item sugar = items.save(new Item(biz.getId(), "Sugar", Unit.KG, null, null));
        stockService.receive(owner.getId(), shop.getId(), sugar.getId(),
                new StockInRequest(UUID.randomUUID(), new BigDecimal("50"), null, 600000L, null));
        inventoryService.update(owner.getId(), shop.getId(), sugar.getId(),
                new UpdateShopItemRequest(19000L, BigDecimal.ZERO));
        dayService.open(cashier.getId(), shop.getId(), new OpenDayRequest(0L));
        long ledgerBefore = movements.count();

        CreateSaleRequest request = new CreateSaleRequest(
                List.of(new CreateSaleRequest.LineRequest(sugar.getId(), new BigDecimal("2"))),
                List.of(new CreateSaleRequest.AllocationRequest(PaymentMethod.CASH, 38000L)));

        // the sale is written completely, then something fails before the transaction commits
        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            saleService.create(cashier.getId(), shop.getId(), UUID.randomUUID(), request);
            throw new IllegalStateException("something fails right after the sale was written");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(sales.count()).isZero();
        assertThat(saleLines.count()).isZero();
        assertThat(movements.count()).isEqualTo(ledgerBefore);
        assertThat(inventory.findByShopIdAndItemId(shop.getId(), sugar.getId()).orElseThrow().getOnHand())
                .isEqualByComparingTo("50");
        assertThat(jdbc.queryForObject("select last_receipt_no from shop_counters where shop_id = ?",
                Long.class, shop.getId())).isZero();

        // the shop works normally afterwards, and this time the sale really commits (the DB checks run too)
        SaleService.SaleResult ok = saleService.create(cashier.getId(), shop.getId(), UUID.randomUUID(), request);
        assertThat(ok.sale().receiptNo()).isEqualTo(1);
        assertThat(sales.count()).isEqualTo(1);
        assertThat(inventory.findByShopIdAndItemId(shop.getId(), sugar.getId()).orElseThrow().getOnHand())
                .isEqualByComparingTo("48");
    }
}