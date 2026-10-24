package com.dukaclose.api.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.business.*;
import com.dukaclose.api.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

class InventoryApiTest extends AbstractApiTest {

    static final String OWNER_A = "+254700000201";
    static final String OWNER_B = "+254700000202";
    static final String CASHIER = "+254700000211";
    static final String PW = "pass12345";

    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    Business bizA, bizB;
    Shop a1, a2, b1;

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
    }

    // ---------- helpers ----------

    private String itemsUrl(Business b) { return "/api/v1/businesses/" + b.getId() + "/items"; }
    private String inventoryUrl(Shop s) { return "/api/v1/shops/" + s.getId() + "/inventory"; }

    private static String q(String v) { return v == null ? "null" : "\"" + v + "\""; }

    private String itemJson(String name, String unit, String packName, String packSize, Long price) {
        return "{\"name\":%s,\"unit\":%s,\"packName\":%s,\"packSize\":%s,\"initialPriceMinor\":%s}"
                .formatted(q(name), q(unit), q(packName), packSize, price);
    }

    private String updateJson(String name, String packName, String packSize, boolean active) {
        return "{\"name\":%s,\"packName\":%s,\"packSize\":%s,\"active\":%s}"
                .formatted(q(name), q(packName), packSize, active);
    }

    private UUID createItem(MockHttpSession owner, Business b, String json) throws Exception {
        String body = doPost(owner, itemsUrl(b), json)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    // ---------- creating and listing ----------

    @Test
    void ownerCreatesItemWithInitialPriceAndEveryShopOfTheBusinessListsIt() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        createItem(owner, bizA, itemJson("Sugar", "KG", "Sack", "50", 19000L));

        for (Shop shop : new Shop[]{a1, a2}) {
            doGet(owner, inventoryUrl(shop))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Sugar"))
                    .andExpect(jsonPath("$[0].priceMinor").value(19000))
                    .andExpect(jsonPath("$[0].packName").value("Sack"));
        }
        // the other business sees nothing
        doGet(loginAs(OWNER_B, PW), inventoryUrl(b1)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cashierSeesPricesAndStockButNotCost() throws Exception {
        createItem(loginAs(OWNER_A, PW), bizA, itemJson("Sugar", "KG", null, null, 19000L));

        doGet(loginAs(CASHIER, PW), inventoryUrl(a1))
                .andExpect(jsonPath("$[0].priceMinor").value(19000))
                .andExpect(jsonPath("$[0].averageCostMinor").isEmpty());

        doGet(loginAs(OWNER_A, PW), inventoryUrl(a1))
                .andExpect(jsonPath("$[0].averageCostMinor").exists());
    }

    @Test
    void cashierCannotCreateItemsListTheCatalogueOrSetPrices() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID itemId = createItem(owner, bizA, itemJson("Sugar", "KG", null, null, 19000L));
        MockHttpSession cashier = loginAs(CASHIER, PW);

        doPost(cashier, itemsUrl(bizA), itemJson("Rice", "KG", null, null, null)).andExpect(status().isForbidden());
        doGet(cashier, itemsUrl(bizA)).andExpect(status().isForbidden());
        doPut(cashier, inventoryUrl(a1) + "/" + itemId, "{\"priceMinor\":1,\"reorderLevel\":0}")
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherBusinessesOwnerCannotTouchMyCatalogueOrShops() throws Exception {
        MockHttpSession ownerA = loginAs(OWNER_A, PW);
        UUID itemId = createItem(ownerA, bizA, itemJson("Sugar", "KG", null, null, 19000L));
        MockHttpSession ownerB = loginAs(OWNER_B, PW);

        doPost(ownerB, itemsUrl(bizA), itemJson("Planted", "KG", null, null, null)).andExpect(status().isForbidden());
        doGet(ownerB, itemsUrl(bizA)).andExpect(status().isForbidden());
        doGet(ownerB, inventoryUrl(a1)).andExpect(status().isForbidden());
        doPut(ownerB, inventoryUrl(a1) + "/" + itemId, "{\"priceMinor\":1,\"reorderLevel\":0}")
                .andExpect(status().isForbidden());
        // my item id used on his own shop: not found
        doPut(ownerB, inventoryUrl(b1) + "/" + itemId, "{\"priceMinor\":1,\"reorderLevel\":0}")
                .andExpect(status().isNotFound());
    }

    // ---------- units and packs ----------

    @Test
    void unitAndPackRulesAreEnforced() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        // pieces are whole: a pack of 1.5 pieces makes no sense
        doPost(owner, itemsUrl(bizA), itemJson("Soda", "PIECE", "Carton", "1.5", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PACK"));
        // a pack needs both a name and a size
        doPost(owner, itemsUrl(bizA), itemJson("Soda", "PIECE", "Carton", null, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PACK"));
        // unknown unit
        doPost(owner, itemsUrl(bizA), itemJson("Soda", "BOX", null, null, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        // valid: carton of 24 pieces, and loose sugar in kilos
        doPost(owner, itemsUrl(bizA), itemJson("Soda", "PIECE", "Carton", "24", null)).andExpect(status().isCreated());
        doPost(owner, itemsUrl(bizA), itemJson("Sugar", "KG", null, null, null)).andExpect(status().isCreated());
    }

    @Test
    void itemNamesAreUniqueIgnoringCase() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID sugar = createItem(owner, bizA, itemJson("Sugar", "KG", null, null, null));
        UUID rice = createItem(owner, bizA, itemJson("Rice", "KG", null, null, null));

        doPost(owner, itemsUrl(bizA), itemJson("SUGAR", "KG", null, null, null)).andExpect(status().isConflict());
        doPut(owner, itemsUrl(bizA) + "/" + rice, updateJson("sugar", null, null, true)).andExpect(status().isConflict());
        // renaming an item to its own name (different case) is fine
        doPut(owner, itemsUrl(bizA) + "/" + sugar, updateJson("SUGAR", null, null, true)).andExpect(status().isOk());
    }

    // ---------- shop prices and reorder levels ----------

    @Test
    void settingAPriceCreatesTheShopRowAndLowStockFollowsTheReorderLevel() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID itemId = createItem(owner, bizA, itemJson("Rice", "KG", null, null, null));

        // not listed in the shop yet: no price, not flagged
        doGet(owner, inventoryUrl(a1))
                .andExpect(jsonPath("$[0].priceMinor").isEmpty())
                .andExpect(jsonPath("$[0].lowStock").value(false));

        doPut(owner, inventoryUrl(a1) + "/" + itemId, "{\"priceMinor\":18000,\"reorderLevel\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceMinor").value(18000))
                .andExpect(jsonPath("$.lowStock").value(true));     // on hand 0 <= reorder level 5

        // another shop's price is independent
        doGet(owner, inventoryUrl(a2)).andExpect(jsonPath("$[0].priceMinor").isEmpty());
    }

    @Test
    void reorderLevelMustFitTheUnit() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID soda = createItem(owner, bizA, itemJson("Soda", "PIECE", null, null, null));

        doPut(owner, inventoryUrl(a1) + "/" + soda, "{\"priceMinor\":5000,\"reorderLevel\":1.5}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    void negativePricesAreRejected() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID itemId = createItem(owner, bizA, itemJson("Rice", "KG", null, null, null));

        doPut(owner, inventoryUrl(a1) + "/" + itemId, "{\"priceMinor\":-1,\"reorderLevel\":0}")
                .andExpect(status().isBadRequest());
    }

    // ---------- archiving ----------

    @Test
    void anItemWithStockCannotBeArchivedUntilTheStockIsCleared() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID itemId = createItem(owner, bizA, itemJson("Rice", "KG", null, null, 18000L));

        // stock receipts arrive in Step 8, so put stock in directly for this test
        em.flush();
        jdbc.update("update shop_inventory set on_hand = 5 where shop_id = ? and item_id = ?", a1.getId(), itemId);
        em.clear();

        doPut(owner, itemsUrl(bizA) + "/" + itemId, updateJson("Rice", null, null, false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_HAS_STOCK"));

        jdbc.update("update shop_inventory set on_hand = 0 where shop_id = ? and item_id = ?", a1.getId(), itemId);
        em.clear();

        doPut(owner, itemsUrl(bizA) + "/" + itemId, updateJson("Rice", null, null, false)).andExpect(status().isOk());
        doGet(owner, inventoryUrl(a1)).andExpect(jsonPath("$.length()").value(0));   // archived items drop out of the shop list
    }
}