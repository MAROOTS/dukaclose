package com.dukaclose.api.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.business.*;
import com.dukaclose.api.day.*;
import com.dukaclose.api.inventory.*;
import com.dukaclose.api.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

class SalesApiTest extends AbstractApiTest {

    static final String OWNER_A = "+254700000601";
    static final String OWNER_B = "+254700000602";
    static final String CASHIER_A1 = "+254700000611";
    static final String CASHIER_A2 = "+254700000612";
    static final String PW = "pass12345";

    @Autowired ItemRepository items;
    @Autowired ShopInventoryRepository inventoryRepo;
    @Autowired StockMovementRepository movements;
    @Autowired StockService stockService;
    @Autowired InventoryService inventoryService;
    @Autowired ShopDayService dayService;
    @Autowired ShopDayRepository dayRepo;
    @Autowired SaleRepository saleRepo;
    @Autowired SaleLineRepository lineRepo;
    @Autowired BusinessClock clock;

    Business bizA, bizB;
    Shop a1, a2;
    User ownerA, cashierA1, cashierA2;
    Item sugar, soda, rice;

    @BeforeEach
    void world() {
        bizA = businesses.save(new Business("Alpha"));
        bizB = businesses.save(new Business("Bravo"));
        a1 = shops.save(new Shop(bizA.getId(), "Alpha One"));
        a2 = shops.save(new Shop(bizA.getId(), "Alpha Two"));

        ownerA = newUser("Owner A", OWNER_A, PW);
        User ownerB = newUser("Owner B", OWNER_B, PW);
        cashierA1 = newUser("Cashier A1", CASHIER_A1, PW);
        cashierA2 = newUser("Cashier A2", CASHIER_A2, PW);
        businessMemberships.save(new BusinessMembership(bizA.getId(), ownerA.getId(), Role.OWNER));
        businessMemberships.save(new BusinessMembership(bizB.getId(), ownerB.getId(), Role.OWNER));
        shopMemberships.save(new ShopMembership(a1.getId(), cashierA1.getId(), Role.CASHIER));
        shopMemberships.save(new ShopMembership(a2.getId(), cashierA2.getId(), Role.CASHIER));

        sugar = items.save(new Item(bizA.getId(), "Sugar", Unit.KG, null, null));
        soda = items.save(new Item(bizA.getId(), "Soda", Unit.PIECE, null, null));
        rice = items.save(new Item(bizA.getId(), "Rice", Unit.KG, null, null));

        for (Shop shop : List.of(a1, a2)) {
            stock(shop, sugar, "75", 900000);     // KSh 120 per kg
            stock(shop, soda, "48", 96000);       // KSh 20 each
            stock(shop, rice, "10", 100000);      // KSh 100 per kg
            price(shop, sugar, 19000);            // sells at KSh 190 per kg
            price(shop, soda, 5000);              // sells at KSh 50 each (rice has no price)
        }
        dayService.open(cashierA1.getId(), a1.getId(), new OpenDayRequest(0L));    // only Alpha One is open
    }

    // ---------- helpers ----------

    private void stock(Shop shop, Item item, String quantity, long totalCostMinor) {
        stockService.receive(ownerA.getId(), shop.getId(), item.getId(),
                new StockInRequest(UUID.randomUUID(), new BigDecimal(quantity), null, totalCostMinor, null));
    }

    private void price(Shop shop, Item item, long priceMinor) {
        inventoryService.update(ownerA.getId(), shop.getId(), item.getId(),
                new UpdateShopItemRequest(priceMinor, BigDecimal.ZERO));
    }

    private void closeDay(Shop shop) {
        ShopDay day = dayRepo.findByShopIdAndStatus(shop.getId(), DayStatus.OPEN).orElseThrow();
        day.markClosed();
        dayRepo.saveAndFlush(day);
    }

    private String salesUrl(Shop shop) { return "/api/v1/shops/" + shop.getId() + "/sales"; }

    private String line(Item item, String quantity) {
        return "{\"itemId\":\"%s\",\"quantity\":%s}".formatted(item.getId(), quantity);
    }

    private String pay(String method, long amountMinor) {
        return "{\"method\":\"%s\",\"amountMinor\":%d}".formatted(method, amountMinor);
    }

    private String body(String lines, String payments) {
        return "{\"lines\":[%s],\"allocations\":[%s]}".formatted(lines, payments);
    }

    private ResultActions sell(MockHttpSession session, Shop shop, String body) throws Exception {
        return doPostWithKey(session, salesUrl(shop), UUID.randomUUID().toString(), body);
    }

    private BigDecimal onHand(Shop shop, Item item) {
        return inventoryRepo.findByShopIdAndItemId(shop.getId(), item.getId()).orElseThrow().getOnHand();
    }

    // ---------- the happy path ----------

    @Test
    void aCashierSellsAndTheServerDoesAllTheMaths() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);

        // 2.5 kg sugar at 190 = 475, plus 3 sodas at 50 = 150: total KSh 625, paid three ways
        sell(cashier, a1, body(line(sugar, "2.5") + "," + line(soda, "3"),
                pay("CASH", 30000) + "," + pay("POCHI", 12500) + "," + pay("TILL", 20000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNo").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalMinor").value(62500))
                .andExpect(jsonPath("$.cashierName").value("Cashier A1"))
                .andExpect(jsonPath("$.businessDate").value(clock.today().toString()))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].name").value("Sugar"))
                .andExpect(jsonPath("$.lines[0].unitPriceMinor").value(19000))
                .andExpect(jsonPath("$.lines[0].lineTotalMinor").value(47500))
                .andExpect(jsonPath("$.lines[1].lineTotalMinor").value(15000))
                .andExpect(jsonPath("$.allocations.length()").value(3));

        assertThat(onHand(a1, sugar)).isEqualByComparingTo("72.5");
        assertThat(onHand(a1, soda)).isEqualByComparingTo("45");
        assertThat(onHand(a2, sugar)).isEqualByComparingTo("75");      // the other shop is untouched
    }

    @Test
    void ownersCanRecordSalesToo() throws Exception {
        sell(loginAs(OWNER_A, PW), a1, body(line(soda, "2"), pay("CASH", 10000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cashierName").value("Owner A"));
    }

    @Test
    void priceAndCostAreSnapshottedAndLaterChangesDoNotRewriteTheSale() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        String response = sell(cashier, a1, body(line(sugar, "2.5"), pay("CASH", 47500)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].unitCostMinor").doesNotExist())     // cashiers never see cost
                .andReturn().getResponse().getContentAsString();
        UUID saleId = UUID.fromString(JsonPath.read(response, "$.id"));

        SaleLine saved = lineRepo.findBySaleIdOrderByLineNo(saleId).get(0);
        assertThat(saved.getUnitPriceMinor()).isEqualTo(19000);
        assertThat(saved.getUnitCost()).isEqualByComparingTo("12000");
        assertThat(saved.getLineCostMinor()).isEqualTo(30000);              // 2.5 kg at KSh 120

        // a new price and a dearer delivery change nothing that was already sold
        price(a1, sugar, 25000);
        stock(a1, sugar, "10", 200000);
        doGet(cashier, salesUrl(a1) + "/" + saleId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPriceMinor").value(19000))
                .andExpect(jsonPath("$.totalMinor").value(47500));
        assertThat(lineRepo.findBySaleIdOrderByLineNo(saleId).get(0).getUnitCost()).isEqualByComparingTo("12000");
    }

    @Test
    void receiptNumbersCountUpPerShopStartingAtOne() throws Exception {
        dayService.open(cashierA2.getId(), a2.getId(), new OpenDayRequest(0L));
        MockHttpSession cashier1 = loginAs(CASHIER_A1, PW);
        MockHttpSession cashier2 = loginAs(CASHIER_A2, PW);
        String oneSoda = body(line(soda, "1"), pay("CASH", 5000));

        sell(cashier1, a1, oneSoda).andExpect(jsonPath("$.receiptNo").value(1));
        sell(cashier1, a1, oneSoda).andExpect(jsonPath("$.receiptNo").value(2));
        sell(cashier2, a2, oneSoda).andExpect(jsonPath("$.receiptNo").value(1));
        sell(cashier1, a1, oneSoda).andExpect(jsonPath("$.receiptNo").value(3));
    }

    // ---------- payments ----------

    @Test
    void paymentsMustAddUpToTheTotalExactly() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        String oneKg = line(sugar, "1");                 // KSh 190 = 19000

        for (long wrong : new long[]{18999, 19001, 100}) {
            sell(cashier, a1, body(oneKg, pay("CASH", wrong)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ALLOCATION_MISMATCH"));
        }
        sell(cashier, a1, body(oneKg, pay("CASH", 9000) + "," + pay("POCHI", 9999)))      // 18999 across two methods
                .andExpect(status().isBadRequest());

        assertThat(saleRepo.count()).isZero();
        assertThat(onHand(a1, sugar)).isEqualByComparingTo("75");

        // an exact split is accepted, and the failures above did not use up a receipt number
        sell(cashier, a1, body(oneKg, pay("CASH", 9000) + "," + pay("POCHI", 10000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNo").value(1));
    }

    @Test
    void paymentRulesAreEnforced() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        String oneKg = line(sugar, "1");

        sell(cashier, a1, body(oneKg, pay("CASH", 10000) + "," + pay("CASH", 9000)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ALLOCATION"));
        sell(cashier, a1, body(oneKg, pay("CASH", 10000) + "," + pay("DENI", 9000)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DENI_NOT_AVAILABLE"));
        sell(cashier, a1, body(oneKg, pay("CASH", 0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        sell(cashier, a1, body(oneKg, pay("CASH", -5)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        sell(cashier, a1, body(oneKg, ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        sell(cashier, a1, body(oneKg, pay("MPESA", 19000)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        assertThat(saleRepo.count()).isZero();
    }

    // ---------- lines, prices and stock ----------

    @Test
    void lineRulesAreEnforced() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        Item flour = items.save(new Item(bizB.getId(), "Flour", Unit.KG, null, null));
        String cash = pay("CASH", 19000);

        sell(cashier, a1, body("", cash))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        sell(cashier, a1, body(line(sugar, "1") + "," + line(sugar, "2"), cash))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LINES"));
        sell(cashier, a1, body(line(sugar, "0"), cash))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        sell(cashier, a1, body(line(sugar, "-1"), cash))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        sell(cashier, a1, body(line(soda, "1.5"), cash))                  // half a soda
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        sell(cashier, a1, body(line(sugar, "0.0001"), cash))              // too many decimals for kg
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));

        String ghost = "{\"itemId\":\"%s\",\"quantity\":1}".formatted(UUID.randomUUID());
        sell(cashier, a1, body(ghost, cash))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
        sell(cashier, a1, body(line(flour, "1"), cash))                   // an item from another business
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));

        assertThat(saleRepo.count()).isZero();
    }

    @Test
    void stockCannotGoBelowZeroButEverythingOnHandCanBeSold() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);

        sell(cashier, a1, body(line(soda, "49"), pay("CASH", 245000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value(containsString("Soda")));
        assertThat(onHand(a1, soda)).isEqualByComparingTo("48");

        sell(cashier, a1, body(line(soda, "48"), pay("CASH", 240000))).andExpect(status().isCreated());
        assertThat(onHand(a1, soda)).isEqualByComparingTo("0");

        sell(cashier, a1, body(line(soda, "1"), pay("CASH", 5000)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void anItemWithoutAPriceInThisShopCannotBeSold() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        Item beans = items.save(new Item(bizA.getId(), "Beans", Unit.KG, null, null));      // never stocked or priced

        sell(cashier, a1, body(line(rice, "1"), pay("CASH", 10000)))                         // stocked, but no price
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_PRICE"));
        sell(cashier, a1, body(line(beans, "1"), pay("CASH", 10000)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_PRICE"));
        assertThat(saleRepo.count()).isZero();
    }

    // ---------- the day ----------

    @Test
    void salesNeedAnOpenDay() throws Exception {
        sell(loginAs(CASHIER_A2, PW), a2, body(line(soda, "1"), pay("CASH", 5000)))          // Alpha Two never opened
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_OPEN_DAY"));

        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        closeDay(a1);
        sell(cashier, a1, body(line(soda, "1"), pay("CASH", 5000)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_OPEN_DAY"));

        assertThat(saleRepo.count()).isZero();
        assertThat(onHand(a1, soda)).isEqualByComparingTo("48");
    }

    // ---------- failed sales leave nothing behind ----------

    @Test
    void aSaleThatFailsPartWayLeavesNothingBehind() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        long ledgerBefore = movements.count();

        // the sugar is fine, but only 48 sodas exist
        sell(cashier, a1, body(line(sugar, "1") + "," + line(soda, "49"), pay("CASH", 19000 + 49 * 5000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(saleRepo.count()).isZero();
        assertThat(movements.count()).isEqualTo(ledgerBefore);
        assertThat(onHand(a1, sugar)).isEqualByComparingTo("75");
        assertThat(onHand(a1, soda)).isEqualByComparingTo("48");
        sell(cashier, a1, body(line(soda, "1"), pay("CASH", 5000)))                           // no number was used up
                .andExpect(jsonPath("$.receiptNo").value(1));
    }

    // ---------- idempotency ----------

    @Test
    void retryingWithTheSameKeyReturnsTheSameSaleAndSellsOnlyOnce() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        String key = UUID.randomUUID().toString();
        String sale = body(line(sugar, "2"), pay("CASH", 38000));

        String first = doPostWithKey(cashier, salesUrl(a1), key, sale)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = doPostWithKey(cashier, salesUrl(a1), key, sale)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(saleRepo.count()).isEqualTo(1);
        assertThat(onHand(a1, sugar)).isEqualByComparingTo("73");
    }

    @Test
    void theSameKeyForADifferentSaleIsRefused() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        String key = UUID.randomUUID().toString();

        doPostWithKey(cashier, salesUrl(a1), key, body(line(sugar, "2"), pay("CASH", 38000)))
                .andExpect(status().isCreated());
        doPostWithKey(cashier, salesUrl(a1), key, body(line(sugar, "3"), pay("CASH", 57000)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        assertThat(saleRepo.count()).isEqualTo(1);
        assertThat(onHand(a1, sugar)).isEqualByComparingTo("73");
    }

    @Test
    void aRetryAfterTheDayClosedStillReturnsTheOriginalSale() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        String key = UUID.randomUUID().toString();
        String sale = body(line(soda, "1"), pay("CASH", 5000));
        doPostWithKey(cashier, salesUrl(a1), key, sale).andExpect(status().isCreated());

        closeDay(a1);

        doPostWithKey(cashier, salesUrl(a1), key, sale).andExpect(status().isOk());
        assertThat(saleRepo.count()).isEqualTo(1);
    }

    @Test
    void theIdempotencyKeyHeaderIsRequiredAndMustBeAUuid() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        String sale = body(line(soda, "1"), pay("CASH", 5000));

        doPostWithKey(cashier, salesUrl(a1), null, sale)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_HEADER"));
        doPostWithKey(cashier, salesUrl(a1), "not-a-uuid", sale)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(saleRepo.count()).isZero();
    }

    // ---------- access ----------

    @Test
    void onlyPeopleOnTheShopCanSellOrReadItsSales() throws Exception {
        String sale = body(line(soda, "1"), pay("CASH", 5000));

        for (String phone : new String[]{CASHIER_A2, OWNER_B}) {
            doPostWithKey(loginAs(phone, PW), salesUrl(a1), UUID.randomUUID().toString(), sale)
                    .andExpect(status().isForbidden());
        }
        doPostWithKey(null, salesUrl(a1), UUID.randomUUID().toString(), sale).andExpect(status().isUnauthorized());

        assertThat(saleRepo.count()).isZero();
        assertThat(onHand(a1, soda)).isEqualByComparingTo("48");
    }

    @Test
    void aSaleCannotBeReadThroughAnotherShopsUrl() throws Exception {
        dayService.open(cashierA2.getId(), a2.getId(), new OpenDayRequest(0L));
        String response = sell(loginAs(CASHIER_A2, PW), a2, body(line(soda, "1"), pay("CASH", 5000)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String saleId = JsonPath.read(response, "$.id");

        doGet(loginAs(CASHIER_A1, PW), salesUrl(a1) + "/" + saleId).andExpect(status().isNotFound());    // wrong shop in the URL
        doGet(loginAs(CASHIER_A1, PW), salesUrl(a2) + "/" + saleId).andExpect(status().isForbidden());   // not their shop
        doGet(loginAs(CASHIER_A2, PW), salesUrl(a2) + "/" + saleId).andExpect(status().isOk());
    }

    // ---------- the exit gate: sales and the stock ledger agree ----------

    @Test
    void stockStillReconcilesWithTheLedgerAfterSales() throws Exception {
        MockHttpSession cashier = loginAs(CASHIER_A1, PW);
        sell(cashier, a1, body(line(sugar, "2.5") + "," + line(soda, "3"), pay("CASH", 62500)))
                .andExpect(status().isCreated());
        sell(cashier, a1, body(line(sugar, "0.25"), pay("TILL", 4750))).andExpect(status().isCreated());
        sell(cashier, a1, body(line(soda, "49"), pay("CASH", 245000))).andExpect(status().isConflict());   // refused: no change

        assertThat(stockService.findMismatches(a1.getId())).isEmpty();
        assertThat(onHand(a1, sugar)).isEqualByComparingTo("72.25");
        assertThat(onHand(a1, soda)).isEqualByComparingTo("45");
        assertThat(movements.findByShopIdAndItemIdOrderBySeqDesc(a1.getId(), sugar.getId(), PageRequest.of(0, 10)))
                .hasSize(3);                                              // one delivery and two sales
    }
}