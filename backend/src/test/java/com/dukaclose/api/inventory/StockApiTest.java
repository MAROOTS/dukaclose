package com.dukaclose.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.business.*;
import com.dukaclose.api.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

class StockApiTest extends AbstractApiTest {

    static final String OWNER_A = "+254700000301";
    static final String OWNER_B = "+254700000302";
    static final String CASHIER = "+254700000311";
    static final String PW = "pass12345";

    @Autowired ItemRepository items;
    @Autowired StockService stock;
    @Autowired StockMovementRepository movements;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    Business bizA, bizB;
    Shop a1, a2, b1;
    Item sugar, soda, rice;

    @BeforeEach
    void world() {
        bizA = businesses.save(new Business("Alpha"));
        bizB = businesses.save(new Business("Bravo"));
        a1 = shops.save(new Shop(bizA.getId(), "Alpha One"));
        a2 = shops.save(new Shop(bizA.getId(), "Alpha Two"));
        b1 = shops.save(new Shop(bizB.getId(), "Bravo One"));
        User ownerA = newUser("Owner A", OWNER_A, PW);
        User ownerB = newUser("Owner B", OWNER_B, PW);
        User cashier = newUser("Cashier", CASHIER, PW);
        businessMemberships.save(new BusinessMembership(bizA.getId(), ownerA.getId(), Role.OWNER));
        businessMemberships.save(new BusinessMembership(bizB.getId(), ownerB.getId(), Role.OWNER));
        shopMemberships.save(new ShopMembership(a1.getId(), cashier.getId(), Role.CASHIER));

        sugar = items.save(new Item(bizA.getId(), "Sugar", Unit.KG, "Sack", new BigDecimal("50")));
        soda = items.save(new Item(bizA.getId(), "Soda", Unit.PIECE, "Carton", new BigDecimal("24")));
        rice = items.save(new Item(bizA.getId(), "Rice", Unit.KG, null, null));
    }

    // ---------- helpers ----------

    private String url(Shop shop, Item item, String path) {
        return "/api/v1/shops/%s/inventory/%s/%s".formatted(shop.getId(), item.getId(), path);
    }

    private static String q(String v) { return v == null ? "null" : "\"" + v + "\""; }

    private String stockIn(UUID requestId, String quantity, String packs, Long cost) {
        return "{\"requestId\":\"%s\",\"quantity\":%s,\"packs\":%s,\"totalCostMinor\":%s,\"note\":null}"
                .formatted(requestId, quantity, packs, cost);
    }

    private String stockIn(String quantity, String packs, Long cost) {
        return stockIn(UUID.randomUUID(), quantity, packs, cost);
    }

    private String adjust(String delta, String reason) {
        return "{\"requestId\":\"%s\",\"quantityDelta\":%s,\"reason\":%s,\"note\":null}"
                .formatted(UUID.randomUUID(), delta, q(reason));
    }

    private Object cell(MockHttpSession session, Shop shop, String itemName, String field) throws Exception {
        String body = doGet(session, "/api/v1/shops/" + shop.getId() + "/inventory")
                .andReturn().getResponse().getContentAsString();
        List<Object> found = JsonPath.read(body, "$[?(@.name=='" + itemName + "')]." + field);
        return found.get(0);
    }

    private BigDecimal number(MockHttpSession session, Shop shop, String itemName, String field) throws Exception {
        return new BigDecimal(String.valueOf(cell(session, shop, itemName, field)));
    }

    // ---------- weighted average cost ----------

    @Test
    void openingStockThenReceiptsGiveAWeightedAverage() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        // 10 kg for KSh 1,000 -> KSh 100 per kg (10000 cents)
        doPost(owner, url(a1, rice, "opening"), stockIn("10", null, 100000L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OPENING"))
                .andExpect(jsonPath("$.balanceAfter").value(10))
                .andExpect(jsonPath("$.unitCostMinor").value(10000));
        assertThat(number(owner, a1, "Rice", "onHand")).isEqualByComparingTo("10");
        assertThat(number(owner, a1, "Rice", "averageCostMinor")).isEqualByComparingTo("10000");

        // 10 more kg for KSh 1,400 -> (10*100 + 1400) / 20 = KSh 120 per kg
        doPost(owner, url(a1, rice, "receipts"), stockIn("10", null, 140000L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(20));
        assertThat(number(owner, a1, "Rice", "averageCostMinor")).isEqualByComparingTo("12000");

        // 5 free kg pull the average down: (20*120 + 0) / 25 = KSh 96
        doPost(owner, url(a1, rice, "receipts"), stockIn("5", null, 0L)).andExpect(status().isCreated());
        assertThat(number(owner, a1, "Rice", "onHand")).isEqualByComparingTo("25");
        assertThat(number(owner, a1, "Rice", "averageCostMinor")).isEqualByComparingTo("9600");
    }

    @Test
    void receivingByPacksConvertsToTheSellingUnit() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        // 2 cartons of 24 sodas for KSh 960 -> 48 pieces at KSh 20 each
        doPost(owner, url(a1, soda, "receipts"), stockIn(null, "2", 96000L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityDelta").value(48))
                .andExpect(jsonPath("$.unitCostMinor").value(2000));
        assertThat(number(owner, a1, "Soda", "onHand")).isEqualByComparingTo("48");

        // 1.5 sacks of 50 kg sugar = 75 kg
        doPost(owner, url(a1, sugar, "receipts"), stockIn(null, "1.5", 900000L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityDelta").value(75));
    }

    @Test
    void quantityRulesAreEnforced() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        doPost(owner, url(a1, rice, "receipts"), stockIn("5", "1", 100L))        // both given
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        doPost(owner, url(a1, rice, "receipts"), stockIn(null, null, 100L))      // neither given
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        doPost(owner, url(a1, rice, "receipts"), stockIn(null, "2", 100L))       // rice has no pack
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NO_PACK"));
        doPost(owner, url(a1, soda, "receipts"), stockIn("1.5", null, 100L))     // half a soda
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        doPost(owner, url(a1, rice, "receipts"), stockIn("1.2345", null, 100L))  // too many decimals for kg
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        doPost(owner, url(a1, rice, "receipts"), stockIn("0", null, 100L)).andExpect(status().isBadRequest());
        doPost(owner, url(a1, rice, "receipts"), stockIn("-3", null, 100L)).andExpect(status().isBadRequest());
        doPost(owner, url(a1, rice, "receipts"), stockIn("5", null, -1L)).andExpect(status().isBadRequest());
        doPost(owner, url(a1, rice, "receipts"), stockIn("5", null, null)).andExpect(status().isBadRequest());

        assertThat(movements.count()).isZero();
    }

    @Test
    void openingStockCanOnlyBeRecordedOnce() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        doPost(owner, url(a1, rice, "opening"), stockIn("10", null, 100000L)).andExpect(status().isCreated());
        doPost(owner, url(a1, rice, "opening"), stockIn("10", null, 100000L))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPENING_ALREADY_SET"));

        // not allowed after a receipt either
        doPost(owner, url(a1, sugar, "receipts"), stockIn("5", null, 1000L)).andExpect(status().isCreated());
        doPost(owner, url(a1, sugar, "opening"), stockIn("5", null, 1000L)).andExpect(status().isConflict());

        assertThat(number(owner, a1, "Rice", "onHand")).isEqualByComparingTo("10");
    }

    @Test
    void shopsKeepSeparateStockAndCost() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        doPost(owner, url(a1, rice, "receipts"), stockIn("10", null, 100000L)).andExpect(status().isCreated());
        doPost(owner, url(a2, rice, "receipts"), stockIn("4", null, 80000L)).andExpect(status().isCreated());

        assertThat(number(owner, a1, "Rice", "averageCostMinor")).isEqualByComparingTo("10000");
        assertThat(number(owner, a2, "Rice", "averageCostMinor")).isEqualByComparingTo("20000");
        assertThat(number(owner, a2, "Rice", "onHand")).isEqualByComparingTo("4");
    }

    // ---------- adjustments ----------

    @Test
    void adjustmentsRemoveAndAddStockWithoutChangingTheAverage() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        doPost(owner, url(a1, rice, "opening"), stockIn("10", null, 100000L)).andExpect(status().isCreated());

        doPost(owner, url(a1, rice, "adjustments"), adjust("-3", "DAMAGED"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(7))
                .andExpect(jsonPath("$.unitCostMinor").value(10000))   // lost at the current average
                .andExpect(jsonPath("$.reason").value("DAMAGED"));

        doPost(owner, url(a1, rice, "adjustments"), adjust("2", "FOUND")).andExpect(status().isCreated());

        assertThat(number(owner, a1, "Rice", "onHand")).isEqualByComparingTo("9");
        assertThat(number(owner, a1, "Rice", "averageCostMinor")).isEqualByComparingTo("10000");
    }

    @Test
    void stockCannotGoNegative() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        doPost(owner, url(a1, rice, "opening"), stockIn("7", null, 70000L)).andExpect(status().isCreated());

        doPost(owner, url(a1, rice, "adjustments"), adjust("-8", "LOST"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        // an item the shop has never stocked
        doPost(owner, url(a1, sugar, "adjustments"), adjust("-1", "LOST"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(number(owner, a1, "Rice", "onHand")).isEqualByComparingTo("7");
    }

    @Test
    void adjustmentReasonMustMatchTheDirection() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        doPost(owner, url(a1, rice, "opening"), stockIn("10", null, 100000L)).andExpect(status().isCreated());

        doPost(owner, url(a1, rice, "adjustments"), adjust("2", "DAMAGED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REASON"));
        doPost(owner, url(a1, rice, "adjustments"), adjust("-1", "FOUND"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REASON"));
        doPost(owner, url(a1, rice, "adjustments"), adjust("0", "OTHER")).andExpect(status().isBadRequest());
        doPost(owner, url(a1, soda, "adjustments"), adjust("1.5", "COUNT_CORRECTION"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        // count corrections may go either way
        doPost(owner, url(a1, rice, "adjustments"), adjust("-1", "COUNT_CORRECTION")).andExpect(status().isCreated());
        doPost(owner, url(a1, rice, "adjustments"), adjust("1", "COUNT_CORRECTION")).andExpect(status().isCreated());
    }

    @Test
    void lowStockFollowsTheLedger() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        doPut(owner, "/api/v1/shops/" + a1.getId() + "/inventory/" + rice.getId(),
                "{\"priceMinor\":18000,\"reorderLevel\":5}").andExpect(status().isOk());
        assertThat(cell(owner, a1, "Rice", "lowStock")).isEqualTo(true);       // 0 on hand

        doPost(owner, url(a1, rice, "receipts"), stockIn("10", null, 100000L)).andExpect(status().isCreated());
        assertThat(cell(owner, a1, "Rice", "lowStock")).isEqualTo(false);

        doPost(owner, url(a1, rice, "adjustments"), adjust("-6", "DAMAGED")).andExpect(status().isCreated());
        assertThat(cell(owner, a1, "Rice", "lowStock")).isEqualTo(true);       // 4 <= 5
        // the price set earlier survived the stock changes
        assertThat(number(owner, a1, "Rice", "priceMinor")).isEqualByComparingTo("18000");
    }

    // ---------- idempotency ----------

    @Test
    void sendingTheSameRequestTwiceOnlyCountsOnce() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID requestId = UUID.randomUUID();
        String body = stockIn(requestId, "10", null, 100000L);

        String first = doPost(owner, url(a1, rice, "receipts"), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = doPost(owner, url(a1, rice, "receipts"), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(movements.count()).isEqualTo(1);
        assertThat(number(owner, a1, "Rice", "onHand")).isEqualByComparingTo("10");
    }

    @Test
    void reusingARequestIdForSomethingElseIsRefused() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID requestId = UUID.randomUUID();
        doPost(owner, url(a1, rice, "receipts"), stockIn(requestId, "10", null, 100000L)).andExpect(status().isCreated());

        doPost(owner, url(a1, sugar, "receipts"), stockIn(requestId, "10", null, 100000L))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(movements.count()).isEqualTo(1);
    }

    // ---------- history ----------

    @Test
    void historyListsNewestFirstWithRunningBalances() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        doPost(owner, url(a1, rice, "opening"), stockIn("10", null, 100000L)).andExpect(status().isCreated());
        doPost(owner, url(a1, rice, "receipts"), stockIn("10", null, 140000L)).andExpect(status().isCreated());
        doPost(owner, url(a1, rice, "adjustments"), adjust("-2", "DAMAGED")).andExpect(status().isCreated());

        doGet(owner, url(a1, rice, "movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$[0].balanceAfter").value(18))
                .andExpect(jsonPath("$[1].type").value("RECEIPT"))
                .andExpect(jsonPath("$[1].balanceAfter").value(20))
                .andExpect(jsonPath("$[2].type").value("OPENING"))
                .andExpect(jsonPath("$[2].balanceAfter").value(10))
                .andExpect(jsonPath("$[0].createdByName").value("Owner A"));

        doGet(owner, url(a1, rice, "movements") + "?limit=2").andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- access control ----------

    @Test
    void cashiersAndOutsidersCannotMoveOrReadStock() throws Exception {
        for (String phone : new String[]{CASHIER, OWNER_B}) {
            MockHttpSession session = loginAs(phone, PW);
            doPost(session, url(a1, rice, "opening"), stockIn("10", null, 100L)).andExpect(status().isForbidden());
            doPost(session, url(a1, rice, "receipts"), stockIn("10", null, 100L)).andExpect(status().isForbidden());
            doPost(session, url(a1, rice, "adjustments"), adjust("1", "FOUND")).andExpect(status().isForbidden());
            doGet(session, url(a1, rice, "movements")).andExpect(status().isForbidden());
        }
        assertThat(movements.count()).isZero();
    }

    @Test
    void anotherBusinessesOwnerCannotUseMyItemsOnTheirOwnShop() throws Exception {
        MockHttpSession ownerB = loginAs(OWNER_B, PW);

        doPost(ownerB, url(b1, rice, "receipts"), stockIn("10", null, 100L)).andExpect(status().isNotFound());
        doGet(ownerB, url(b1, rice, "movements")).andExpect(status().isNotFound());
        assertThat(movements.count()).isZero();
    }

    @Test
    void stockEndpointsRequireLogin() throws Exception {
        doPost(null, url(a1, rice, "receipts"), stockIn("10", null, 100L)).andExpect(status().isUnauthorized());
        doGet(null, url(a1, rice, "movements")).andExpect(status().isUnauthorized());
    }

    // ---------- the exit gate: on-hand reconciles with the ledger ----------

    @Test
    void onHandAlwaysEqualsTheSumOfTheLedger() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        doPost(owner, url(a1, rice, "opening"), stockIn("10", null, 100000L)).andExpect(status().isCreated());
        doPost(owner, url(a1, rice, "receipts"), stockIn("12.5", null, 150000L)).andExpect(status().isCreated());
        doPost(owner, url(a1, rice, "adjustments"), adjust("-4.25", "EXPIRED")).andExpect(status().isCreated());
        doPost(owner, url(a1, soda, "receipts"), stockIn(null, "3", 144000L)).andExpect(status().isCreated());
        doPost(owner, url(a1, soda, "adjustments"), adjust("-5", "DAMAGED")).andExpect(status().isCreated());
        doPost(owner, url(a1, sugar, "adjustments"), adjust("-1", "LOST")).andExpect(status().isConflict());   // refused: no change
        doPost(owner, url(a2, rice, "receipts"), stockIn("3", null, 30000L)).andExpect(status().isCreated());

        assertThat(stock.findMismatches(a1.getId())).isEmpty();
        assertThat(stock.findMismatches(a2.getId())).isEmpty();
        assertThat(number(owner, a1, "Rice", "onHand")).isEqualByComparingTo("18.25");
        assertThat(number(owner, a1, "Soda", "onHand")).isEqualByComparingTo("67");
    }

    @Test
    void theReconciliationCheckActuallyCatchesDrift() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        doPost(owner, url(a1, rice, "opening"), stockIn("10", null, 100000L)).andExpect(status().isCreated());
        assertThat(stock.findMismatches(a1.getId())).isEmpty();

        // someone edits the balance behind the application's back
        em.flush();
        jdbc.update("update shop_inventory set on_hand = on_hand + 1 where shop_id = ? and item_id = ?",
                a1.getId(), rice.getId());
        em.clear();

        List<StockService.Mismatch> bad = stock.findMismatches(a1.getId());
        assertThat(bad).hasSize(1);
        assertThat(bad.get(0).itemId()).isEqualTo(rice.getId());
    }

    // ---------- the ledger cannot be rewritten (one statement per test: a failure aborts the transaction) ----------

    @Test
    void ledgerRowsCannotBeEdited() throws Exception {
        doPost(loginAs(OWNER_A, PW), url(a1, rice, "receipts"), stockIn("10", null, 100000L)).andExpect(status().isCreated());
        em.flush();

        assertThatThrownBy(() -> jdbc.update("update stock_movements set quantity_delta = 99"))
                .hasMessageContaining("append-only");
    }

    @Test
    void ledgerRowsCannotBeDeleted() throws Exception {
        doPost(loginAs(OWNER_A, PW), url(a1, rice, "receipts"), stockIn("10", null, 100000L)).andExpect(status().isCreated());
        em.flush();

        assertThatThrownBy(() -> jdbc.update("delete from stock_movements"))
                .hasMessageContaining("append-only");
    }
}