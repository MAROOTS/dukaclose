package com.dukaclose.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.business.*;
import com.dukaclose.api.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

class CsvImportApiTest extends AbstractApiTest {

    static final String OWNER_A = "+254700000401";
    static final String OWNER_B = "+254700000402";
    static final String CASHIER = "+254700000411";
    static final String PW = "pass12345";

    static final String GOOD = """
            name,unit,price,quantity,cost_per_unit,reorder_level
            Sugar,kg,190,75,120,20
            Soda 500ml,pcs,50,48,20,24
            Cooking oil,litre,350.50,,,5
            """;

    @Autowired ItemRepository items;
    @Autowired StockService stock;
    @Autowired StockMovementRepository movements;

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

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "")
                .replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    private ResultActions send(MockHttpSession session, Shop shop, UUID importId, String csv, boolean commit)
            throws Exception {
        return doPost(session, "/api/v1/shops/%s/inventory/import?commit=%s".formatted(shop.getId(), commit),
                "{\"importId\":\"%s\",\"csv\":%s}".formatted(importId, json(csv)));
    }

    private BigDecimal number(MockHttpSession session, Shop shop, String itemName, String field) throws Exception {
        String body = doGet(session, "/api/v1/shops/" + shop.getId() + "/inventory")
                .andReturn().getResponse().getContentAsString();
        List<Object> found = JsonPath.read(body, "$[?(@.name=='" + itemName + "')]." + field);
        return new BigDecimal(String.valueOf(found.get(0)));
    }

    // ---------- preview and commit ----------

    @Test
    void previewShowsTheSummaryAndSavesNothing() throws Exception {
        send(loginAs(OWNER_A, PW), a1, UUID.randomUUID(), GOOD, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(false))
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.rowsRead").value(3))
                .andExpect(jsonPath("$.newItems").value(3))
                .andExpect(jsonPath("$.existingItems").value(0))
                .andExpect(jsonPath("$.openingStockRows").value(2))
                .andExpect(jsonPath("$.openingValueMinor").value(996000));   // 75*120 + 48*20 = KSh 9,960

        assertThat(items.findByBusinessIdOrderByNameAsc(bizA.getId())).isEmpty();
        assertThat(movements.count()).isZero();
    }

    @Test
    void commitCreatesItemsPricesAndStartingStockThatReconcile() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        send(owner, a1, UUID.randomUUID(), GOOD, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true));

        assertThat(number(owner, a1, "Sugar", "onHand")).isEqualByComparingTo("75");
        assertThat(number(owner, a1, "Sugar", "priceMinor")).isEqualByComparingTo("19000");
        assertThat(number(owner, a1, "Sugar", "averageCostMinor")).isEqualByComparingTo("12000");
        assertThat(number(owner, a1, "Sugar", "reorderLevel")).isEqualByComparingTo("20");
        assertThat(number(owner, a1, "Soda 500ml", "onHand")).isEqualByComparingTo("48");
        assertThat(number(owner, a1, "Cooking oil", "priceMinor")).isEqualByComparingTo("35050");
        assertThat(number(owner, a1, "Cooking oil", "onHand")).isEqualByComparingTo("0");

        // the items exist for the whole business, but the other shop has no stock or price
        assertThat(number(owner, a2, "Sugar", "onHand")).isEqualByComparingTo("0");
        assertThat(stock.findMismatches(a1.getId())).isEmpty();
        assertThat(movements.count()).isEqualTo(2);
    }

    @Test
    void committingTheSameImportTwiceDoesNotDoubleCount() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        UUID importId = UUID.randomUUID();

        send(owner, a1, importId, GOOD, true).andExpect(jsonPath("$.committed").value(true));
        send(owner, a1, importId, GOOD, true)                       // the browser retried after a lost response
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.openingStockRows").value(0));

        assertThat(number(owner, a1, "Sugar", "onHand")).isEqualByComparingTo("75");
        assertThat(movements.count()).isEqualTo(2);
    }

    @Test
    void uploadingTheSameFileAgainAsANewImportIsRefused() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        send(owner, a1, UUID.randomUUID(), GOOD, true).andExpect(jsonPath("$.committed").value(true));

        send(owner, a1, UUID.randomUUID(), GOOD, true)
                .andExpect(jsonPath("$.committed").value(false))
                .andExpect(jsonPath("$.errorCount").value(2))
                .andExpect(jsonPath("$.errors[0].line").value(2))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("already has stock history")));

        assertThat(number(owner, a1, "Sugar", "onHand")).isEqualByComparingTo("75");
        assertThat(movements.count()).isEqualTo(2);
    }

    // ---------- all or nothing ----------

    @Test
    void oneBadRowBlocksTheWholeFile() throws Exception {
        String csv = """
                name,unit,price,quantity,cost_per_unit
                Rice,kg,180,10,100
                Soda,piece,50,1.5,20
                Bread,loaf,60,,
                rice,kg,,,
                """;

        send(loginAs(OWNER_A, PW), a1, UUID.randomUUID(), csv, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(false))
                .andExpect(jsonPath("$.errorCount").value(3))
                .andExpect(jsonPath("$.errors[0].line").value(3))     // half a soda
                .andExpect(jsonPath("$.errors[1].line").value(4))     // unknown unit
                .andExpect(jsonPath("$.errors[2].line").value(5));    // Rice twice, ignoring case

        assertThat(items.findByBusinessIdOrderByNameAsc(bizA.getId())).isEmpty();
        assertThat(movements.count()).isZero();
    }

    @Test
    void quantityAndCostRulesAreEnforcedPerRow() throws Exception {
        String csv = """
                name,unit,quantity,cost_per_unit
                Eggs,piece,1.5,10
                Milk,litre,5,
                Tea,kg,-2,100
                Salt,kg,0,
                """;

        send(loginAs(OWNER_A, PW), a1, UUID.randomUUID(), csv, false)
                .andExpect(jsonPath("$.errorCount").value(3))
                .andExpect(jsonPath("$.errors[0].line").value(2))     // fractional pieces
                .andExpect(jsonPath("$.errors[1].line").value(3))     // quantity without a cost
                .andExpect(jsonPath("$.errors[2].line").value(4));    // negative quantity; Salt with 0 is fine
    }

    // ---------- existing items ----------

    @Test
    void existingItemsAreUpdatedNotDuplicated() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        items.save(new Item(bizA.getId(), "Rice", Unit.KG, null, null));

        send(owner, a1, UUID.randomUUID(), "name,unit,price,reorder_level\nRICE,kg,200,10\n", true)
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.newItems").value(0))
                .andExpect(jsonPath("$.existingItems").value(1));

        assertThat(items.findByBusinessIdOrderByNameAsc(bizA.getId())).hasSize(1);
        assertThat(number(owner, a1, "Rice", "priceMinor")).isEqualByComparingTo("20000");
        assertThat(number(owner, a1, "Rice", "reorderLevel")).isEqualByComparingTo("10");
    }

    @Test
    void anExistingItemCannotChangeUnit() throws Exception {
        items.save(new Item(bizA.getId(), "Rice", Unit.KG, null, null));

        send(loginAs(OWNER_A, PW), a1, UUID.randomUUID(), "name,unit\nRice,piece\n", true)
                .andExpect(jsonPath("$.committed").value(false))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andExpect(jsonPath("$.errors[0].line").value(2));
    }

    @Test
    void packsApplyToNewItemsAndAreIgnoredWithAWarningForExistingOnes() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        items.save(new Item(bizA.getId(), "Rice", Unit.KG, null, null));
        String csv = "name,unit,pack_name,pack_size\nSugar,kg,Sack,50\nRice,kg,Sack,50\nBeans,kg,Sack,\n";

        send(owner, a1, UUID.randomUUID(), csv, false)
                .andExpect(jsonPath("$.errorCount").value(1))                 // Beans: a pack needs a size
                .andExpect(jsonPath("$.errors[0].line").value(4))
                .andExpect(jsonPath("$.warnings.length()").value(1))          // Rice: pack ignored
                .andExpect(jsonPath("$.warnings[0].line").value(3));

        send(owner, a1, UUID.randomUUID(), "name,unit,pack_name,pack_size\nSugar,kg,Sack,50\n", true)
                .andExpect(jsonPath("$.committed").value(true));
        assertThat(number(owner, a1, "Sugar", "packSize")).isEqualByComparingTo("50");
    }

    // ---------- formats ----------

    @Test
    void semicolonsBomAliasesAndQuotedCommasAreUnderstood() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);
        String csv = "\uFEFFItem;Unit;Selling Price;Qty;Cost\n\"Soda, 500ml\";pcs;50;24;20\n";

        send(owner, a1, UUID.randomUUID(), csv, true)
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.committed").value(true));

        assertThat(number(owner, a1, "Soda, 500ml", "priceMinor")).isEqualByComparingTo("5000");
        assertThat(number(owner, a1, "Soda, 500ml", "onHand")).isEqualByComparingTo("24");
        assertThat(number(owner, a1, "Soda, 500ml", "averageCostMinor")).isEqualByComparingTo("2000");
    }

    @Test
    void moneyFormatsAreParsedStrictly() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        // "1,5" could mean 1.5 in another country, so it is refused instead of guessed
        send(owner, a1, UUID.randomUUID(), "name,unit,price\nRice,kg,\"KSh 1,200.50\"\nSugar,kg,190.5\nTea,kg,\"1,5\"\n", false)
                .andExpect(jsonPath("$.errorCount").value(1))
                .andExpect(jsonPath("$.errors[0].line").value(4));

        send(owner, a1, UUID.randomUUID(), "name,unit,price\nRice,kg,\"KSh 1,200.50\"\nSugar,kg,190.5\n", true)
                .andExpect(jsonPath("$.committed").value(true));
        assertThat(number(owner, a1, "Rice", "priceMinor")).isEqualByComparingTo("120050");
        assertThat(number(owner, a1, "Sugar", "priceMinor")).isEqualByComparingTo("19050");
    }

    @Test
    void unusableFilesAreRejectedWithAClearReason() throws Exception {
        MockHttpSession owner = loginAs(OWNER_A, PW);

        send(owner, a1, UUID.randomUUID(), "name,price\nRice,100\n", false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_COLUMNS"));
        send(owner, a1, UUID.randomUUID(), "name,unit\n\n\n", false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_FILE"));

        String tooMany = "name,unit\n" + IntStream.rangeClosed(1, 1001)
                .mapToObj(i -> "Item" + i + ",kg").collect(Collectors.joining("\n"));
        send(owner, a1, UUID.randomUUID(), tooMany, false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOO_MANY_ROWS"));
    }

    // ---------- access ----------

    @Test
    void onlyTheShopsOwnerMayImport() throws Exception {
        for (String phone : new String[]{CASHIER, OWNER_B}) {
            send(loginAs(phone, PW), a1, UUID.randomUUID(), GOOD, true).andExpect(status().isForbidden());
        }
        send(null, a1, UUID.randomUUID(), GOOD, true).andExpect(status().isUnauthorized());

        assertThat(items.findByBusinessIdOrderByNameAsc(bizA.getId())).isEmpty();
        assertThat(movements.count()).isZero();
    }
}