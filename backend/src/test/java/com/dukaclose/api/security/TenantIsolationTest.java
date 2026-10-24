package com.dukaclose.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.business.*;
import com.dukaclose.api.support.AbstractApiTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Phase 1 exit gate. Two businesses, three shops, five people. Every attacker is tried against every victim.
 *
 *   Alpha Traders: shops "Alpha One" (a1), "Alpha Two" (a2); owner A; cashier A1 on a1; cashier A2 on a2
 *   Bravo Traders: shop  "Bravo One" (b1);                    owner B; cashier B1 on b1
 */

class TenantIsolationTest extends AbstractApiTest {
    @Autowired com.dukaclose.api.inventory.ItemRepository itemRepo;
    @Autowired
    com.dukaclose.api.inventory.StockMovementRepository movementRepo;
    static final String PASSWORD = "pass12345";

    Business bizA, bizB;
    Shop a1, a2, b1;
    User ownerA, ownerB, cashierA1, cashierA2, cashierB1;
    BusinessMembership ownerAMembership;

    @BeforeEach
    void world() {
        bizA = businesses.save(new Business("Alpha Traders"));
        bizB = businesses.save(new Business("Bravo Traders"));
        a1 = shops.save(new Shop(bizA.getId(), "Alpha One"));
        a2 = shops.save(new Shop(bizA.getId(), "Alpha Two"));
        b1 = shops.save(new Shop(bizB.getId(), "Bravo One"));

        ownerA = newUser("Owner A", "+254700000101", PASSWORD);
        ownerB = newUser("Owner B", "+254700000102", PASSWORD);
        cashierA1 = newUser("Cashier A1", "+254700000111", PASSWORD);
        cashierA2 = newUser("Cashier A2", "+254700000112", PASSWORD);
        cashierB1 = newUser("Cashier B1", "+254700000121", PASSWORD);

        ownerAMembership = businessMemberships.save(new BusinessMembership(bizA.getId(), ownerA.getId(), Role.OWNER));
        businessMemberships.save(new BusinessMembership(bizB.getId(), ownerB.getId(), Role.OWNER));
        shopMemberships.save(new ShopMembership(a1.getId(), cashierA1.getId(), Role.CASHIER));
        shopMemberships.save(new ShopMembership(a2.getId(), cashierA2.getId(), Role.CASHIER));
        shopMemberships.save(new ShopMembership(b1.getId(), cashierB1.getId(), Role.CASHIER));
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpSession as(User user) throws Exception {
        return loginAs(user.getLoginIdentifier(), PASSWORD);
    }

    private String shopUrl(Shop shop) {
        return "/api/v1/shops/" + shop.getId();
    }

    /** Endpoints only an owner of the shop's business may use. */
    private List<String[]> managementEndpoints(Shop shop, User someone) {
        return List.of(
                new String[]{"GET", shopUrl(shop) + "/cashiers"},
                new String[]{"POST", shopUrl(shop) + "/cashiers/" + someone.getId()},
                new String[]{"DELETE", shopUrl(shop) + "/cashiers/" + someone.getId()});
    }

    private ResultActions fire(MockHttpSession session, String method, String url) throws Exception {
        return switch (method) {
            case "GET" -> doGet(session, url);
            case "POST" -> doPost(session, url, null);
            case "DELETE" -> doDelete(session, url);
            default -> throw new IllegalArgumentException(method);
        };
    }

    private void expectStatus(int expected, MockHttpSession session, String who, String[] endpoint) throws Exception {
        int actual = fire(session, endpoint[0], endpoint[1]).andReturn().getResponse().getStatus();
        assertThat(actual).as("%s -> %s %s", who, endpoint[0], endpoint[1]).isEqualTo(expected);
    }

    /** The attacker must be refused everything on the victim shop: viewing it and managing it. */
    private void assertTotallyLockedOut(User attacker, Shop victim) throws Exception {
        MockHttpSession session = as(attacker);
        String who = attacker.getDisplayName() + " vs " + victim.getName();
        expectStatus(403, session, who, new String[]{"GET", shopUrl(victim)});
        expectStatus(403, session, who, new String[]{"GET", shopUrl(victim) + "/inventory"});
        for (String[] endpoint : managementEndpoints(victim, cashierA1)) {
            expectStatus(403, session, who, endpoint);
        }
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------- cross-tenant walls

    @Test
    void cashierCannotTouchAnotherShopInTheSameBusiness() throws Exception {
        assertTotallyLockedOut(cashierA1, a2);
        assertTotallyLockedOut(cashierA2, a1);
    }

    @Test
    void cashierCannotTouchAnotherBusinessesShop() throws Exception {
        assertTotallyLockedOut(cashierA1, b1);
        assertTotallyLockedOut(cashierB1, a1);
        assertTotallyLockedOut(cashierB1, a2);
    }

    @Test
    void ownerCannotTouchAnotherBusinessesShops() throws Exception {
        assertTotallyLockedOut(ownerB, a1);
        assertTotallyLockedOut(ownerB, a2);
        assertTotallyLockedOut(ownerA, b1);
    }

    @Test
    void cashiersAndForeignOwnersCannotReadTheCatalogue() throws Exception {
        String url = "/api/v1/businesses/" + bizA.getId() + "/items";
        doGet(as(cashierA1), url).andExpect(status().isForbidden());
        doGet(as(ownerB), url).andExpect(status().isForbidden());
    }

    @Test
    void cashierOnTheirOwnShopCanViewButNotManage() throws Exception {
        MockHttpSession session = as(cashierA1);

        expectStatus(200, session, "cashier A1 on a1", new String[]{"GET", shopUrl(a1)});
        for (String[] endpoint : managementEndpoints(a1, cashierA2)) {
            expectStatus(403, session, "cashier A1 on a1", endpoint);
        }
    }

    @Test
    void everythingRequiresLogin() throws Exception {
        expectStatus(401, null, "anonymous", new String[]{"GET", "/api/v1/me"});
        expectStatus(401, null, "anonymous", new String[]{"GET", "/api/v1/shops"});
        expectStatus(401, null, "anonymous", new String[]{"GET", shopUrl(a1)});
        for (String[] endpoint : managementEndpoints(a1, cashierA1)) {
            expectStatus(401, null, "anonymous", endpoint);
        }
        doPost(null, "/api/v1/businesses/" + bizA.getId() + "/cashiers", "{}")
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------- no information leaks

    @Test
    void foreignShopAndNonexistentShopAreIndistinguishable() throws Exception {
        for (User attacker : List.of(cashierA1, ownerB)) {
            MockHttpSession session = as(attacker);

            ResultActions foreign = doGet(session, shopUrl(attacker == ownerB ? a1 : b1));
            ResultActions missing = doGet(session, "/api/v1/shops/" + UUID.randomUUID());

            assertThat(foreign.andReturn().getResponse().getStatus())
                    .isEqualTo(missing.andReturn().getResponse().getStatus())
                    .isEqualTo(403);
            assertThat(body(foreign)).isEqualTo(body(missing));
        }
    }

    @Test
    void listsAndProfilesNeverContainAnotherTenantsData() throws Exception {
        // cashier A1: sees Alpha One only
        MockHttpSession cashier = as(cashierA1);
        String shopList = body(doGet(cashier, "/api/v1/shops"));
        assertThat(shopList).contains("Alpha One").doesNotContain("Alpha Two").doesNotContain("Bravo");
        assertThat(body(doGet(cashier, "/api/v1/me"))).doesNotContain("Alpha Two").doesNotContain("Bravo");

        // owner B: sees Bravo only, in every place a shop or business name can appear
        MockHttpSession owner = as(ownerB);
        assertThat(body(doGet(owner, "/api/v1/shops"))).contains("Bravo One").doesNotContain("Alpha");
        assertThat(body(doGet(owner, "/api/v1/me"))).contains("Bravo Traders").doesNotContain("Alpha");

        // cashier B1: no ownership, one shop
        doGet(as(cashierB1), "/api/v1/me")
                .andExpect(jsonPath("$.businesses.length()").value(0))
                .andExpect(jsonPath("$.shops.length()").value(1));
    }

    // ------------------------------------------- tenants cannot be mixed through the API

    @Test
    void ownerCannotCreateCashiersInAnotherBusiness() throws Exception {
        String json = """
                {"displayName":"Planted","phone":"0733000001","password":"temp-pass-1","shopIds":["%s"]}"""
                .formatted(b1.getId());

        doPost(as(ownerA), "/api/v1/businesses/" + bizB.getId() + "/cashiers", json)
                .andExpect(status().isForbidden());
        assertThat(users.findByLoginIdentifier("+254733000001")).isEmpty();
    }

    @Test
    void ownerCannotAttachANewCashierToAnotherBusinessesShop() throws Exception {
        String json = """
                {"displayName":"Planted","phone":"0733000002","password":"temp-pass-1","shopIds":["%s","%s"]}"""
                .formatted(a1.getId(), b1.getId());

        doPost(as(ownerA), "/api/v1/businesses/" + bizA.getId() + "/cashiers", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHOPS"));
        // all-or-nothing: the valid half of the request did not create the user either
        assertThat(users.findByLoginIdentifier("+254733000002")).isEmpty();
    }

    @Test
    void ownerCannotPullAnotherBusinessesCashierIntoTheirShop() throws Exception {
        doPost(as(ownerA), shopUrl(a1) + "/cashiers/" + cashierB1.getId(), null)
                .andExpect(status().isNotFound());

        doGet(as(cashierB1), "/api/v1/shops")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bravo One"));
    }

    @Test
    void cashierCannotGrantThemselvesAnotherShop() throws Exception {
        MockHttpSession session = as(cashierA1);

        doPost(session, shopUrl(a2) + "/cashiers/" + cashierA1.getId(), null)
                .andExpect(status().isForbidden());

        doGet(session, "/api/v1/shops").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void foreignOwnerCannotRemoveSomeonesCashier() throws Exception {
        doDelete(as(ownerB), shopUrl(a1) + "/cashiers/" + cashierA1.getId())
                .andExpect(status().isForbidden());

        // the cashier still has access
        expectStatus(200, as(cashierA1), "cashier A1 after the attack", new String[]{"GET", shopUrl(a1)});
    }

    // ------------------------ access that changes after login is enforced immediately

    @Test
    void deactivatedShopIsClosedToEveryone() throws Exception {
        MockHttpSession owner = as(ownerA);
        MockHttpSession cashier = as(cashierA1);
        expectStatus(200, owner, "before", new String[]{"GET", shopUrl(a1)});

        a1.deactivate();
        shops.saveAndFlush(a1);

        expectStatus(403, owner, "owner after shop deactivated", new String[]{"GET", shopUrl(a1)});
        expectStatus(403, cashier, "cashier after shop deactivated", new String[]{"GET", shopUrl(a1)});
        doGet(owner, "/api/v1/shops")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alpha Two"));
    }

    @Test
    void deactivatedBusinessIsClosedToOwnersAndCashiers() throws Exception {
        MockHttpSession owner = as(ownerA);
        MockHttpSession cashier = as(cashierA2);

        bizA.deactivate();
        businesses.saveAndFlush(bizA);

        expectStatus(403, owner, "owner after business deactivated", new String[]{"GET", shopUrl(a2)});
        expectStatus(403, cashier, "cashier after business deactivated", new String[]{"GET", shopUrl(a2)});
        doGet(owner, "/api/v1/me")
                .andExpect(jsonPath("$.businesses.length()").value(0))
                .andExpect(jsonPath("$.shops.length()").value(0));
    }

    @Test
    void revokedOwnerLosesAccessToTheirOwnShopsMidSession() throws Exception {
        MockHttpSession owner = as(ownerA);
        expectStatus(200, owner, "before", new String[]{"GET", shopUrl(a1)});

        ownerAMembership.deactivate();
        businessMemberships.saveAndFlush(ownerAMembership);

        assertTotallyLockedOut(ownerA, a1);
        doGet(owner, "/api/v1/me")
                .andExpect(jsonPath("$.businesses.length()").value(0))
                .andExpect(jsonPath("$.shops.length()").value(0));
    }

    @Test
    void stockEndpointsAreClosedToEveryoneExceptTheShopsOwner() throws Exception {
        var sugar = itemRepo.save(new com.dukaclose.api.inventory.Item(
                bizA.getId(), "Sugar", com.dukaclose.api.inventory.Unit.KG, null, null));
        String base = shopUrl(a1) + "/inventory/" + sugar.getId();
        String receipt = "{\"requestId\":\"%s\",\"quantity\":5,\"totalCostMinor\":1000}".formatted(UUID.randomUUID());
        String adjustment = "{\"requestId\":\"%s\",\"quantityDelta\":1,\"reason\":\"FOUND\"}".formatted(UUID.randomUUID());

        for (User attacker : List.of(cashierA1, cashierA2, ownerB, cashierB1)) {
            MockHttpSession session = as(attacker);
            doPost(session, base + "/opening", receipt).andExpect(status().isForbidden());
            doPost(session, base + "/receipts", receipt).andExpect(status().isForbidden());
            doPost(session, base + "/adjustments", adjustment).andExpect(status().isForbidden());
            doGet(session, base + "/movements").andExpect(status().isForbidden());
        }
        assertThat(movementRepo.count()).isZero();
    }

    @Test
    void importIsClosedToEveryoneExceptTheShopsOwner() throws Exception {
        String body = "{\"importId\":\"%s\",\"csv\":\"name,unit\\nRice,kg\"}".formatted(UUID.randomUUID());
        for (User attacker : List.of(cashierA1, cashierA2, ownerB, cashierB1)) {
            doPost(as(attacker), shopUrl(a1) + "/inventory/import?commit=true", body)
                    .andExpect(status().isForbidden());
        }
        assertThat(itemRepo.findByBusinessIdOrderByNameAsc(bizA.getId())).isEmpty();
    }
}