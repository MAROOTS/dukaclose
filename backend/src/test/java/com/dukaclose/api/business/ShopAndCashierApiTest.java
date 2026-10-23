package com.dukaclose.api.business;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.support.AbstractApiTest;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class ShopAndCashierApiTest extends AbstractApiTest {

    static final String OWNER = "+254700000001";
    static final String OTIENO = "+254700000002";

    Business business, rival;
    Shop kikuyu, limuru, rivalShop;
    User owner, otieno, rivalOwner;

    @BeforeEach
    void fixtures() {
        business = businesses.save(new Business("Mama Njeri Stores"));
        kikuyu = shops.save(new Shop(business.getId(), "Kikuyu"));
        limuru = shops.save(new Shop(business.getId(), "Limuru"));
        owner = newUser("Njeri", OWNER, "owner123");
        businessMemberships.save(new BusinessMembership(business.getId(), owner.getId(), Role.OWNER));
        otieno = newUser("Otieno", OTIENO, "cashier123");
        shopMemberships.save(new ShopMembership(kikuyu.getId(), otieno.getId(), Role.CASHIER));

        rival = businesses.save(new Business("Rival Traders"));
        rivalShop = shops.save(new Shop(rival.getId(), "Rival Main"));
        rivalOwner = newUser("Rival", "+254700000009", "rival123");
        businessMemberships.save(new BusinessMembership(rival.getId(), rivalOwner.getId(), Role.OWNER));
    }

    private String cashierJson(String name, String phone, String password, UUID... shopIds) {
        String ids = Arrays.stream(shopIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return """
                {"displayName":"%s","phone":"%s","password":"%s","shopIds":[%s]}""".formatted(name, phone, password, ids);
    }

    private String cashiersUrl(Business b) {
        return "/api/v1/businesses/" + b.getId() + "/cashiers";
    }

    // ---------- shops ----------

    @Test
    void ownerCreatesShop() throws Exception {
        doPost(loginAs(OWNER, "owner123"), "/api/v1/shops",
                """
                {"businessId":"%s","name":"  Ruiru Branch "}""".formatted(business.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ruiru Branch"))
                .andExpect(jsonPath("$.myRole").value("OWNER"));
    }

    @Test
    void cashierCannotCreateShop() throws Exception {
        doPost(loginAs(OTIENO, "cashier123"), "/api/v1/shops",
                """
                {"businessId":"%s","name":"Sneaky"}""".formatted(business.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotCreateShopInSomeoneElsesBusiness() throws Exception {
        doPost(loginAs(OWNER, "owner123"), "/api/v1/shops",
                """
                {"businessId":"%s","name":"Hijack"}""".formatted(rival.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateShopNameInSameBusinessIsRejected() throws Exception {
        doPost(loginAs(OWNER, "owner123"), "/api/v1/shops",
                """
                {"businessId":"%s","name":"Kikuyu"}""".formatted(business.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void cashierSeesOnlyTheirShopAndCannotOpenOthers() throws Exception {
        MockHttpSession session = loginAs(OTIENO, "cashier123");

        doGet(session, "/api/v1/shops")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Kikuyu"));
        doGet(session, "/api/v1/shops/" + limuru.getId()).andExpect(status().isForbidden());
        doGet(session, "/api/v1/shops/" + UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    void ownerOfAnotherBusinessCannotOpenMyShop() throws Exception {
        doGet(loginAs("+254700000009", "rival123"), "/api/v1/shops/" + kikuyu.getId())
                .andExpect(status().isForbidden());
    }

    // ---------- cashiers ----------

    @Test
    void ownerCreatesCashierAndPhoneFormatsAreNormalised() throws Exception {
        MockHttpSession ownerSession = loginAs(OWNER, "owner123");

        doPost(ownerSession, cashiersUrl(business),
                cashierJson("Wanjiru", "0712 345 678", "temp-pass-1", limuru.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+254712345678"));

        // Both spellings of the number now log in, and she sees only Limuru
        loginAs("0712345678", "temp-pass-1");
        doGet(loginAs("+254712345678", "temp-pass-1"), "/api/v1/shops")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Limuru"));
    }

    @Test
    void duplicatePhoneIsRejectedEvenInAnotherFormat() throws Exception {
        doPost(loginAs(OWNER, "owner123"), cashiersUrl(business),
                cashierJson("Copy", "0700000002", "temp-pass-1", limuru.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotPutACashierOnAnotherBusinessesShop() throws Exception {
        doPost(loginAs(OWNER, "owner123"), cashiersUrl(business),
                cashierJson("Wanjiru", "0712345678", "temp-pass-1", rivalShop.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cashierCannotManageCashiers() throws Exception {
        MockHttpSession session = loginAs(OTIENO, "cashier123");

        doPost(session, cashiersUrl(business),
                cashierJson("Friend", "0712345678", "temp-pass-1", kikuyu.getId()))
                .andExpect(status().isForbidden());
        doGet(session, "/api/v1/shops/" + kikuyu.getId() + "/cashiers").andExpect(status().isForbidden());
    }

    @Test
    void ownerListsCashiersOfAShop() throws Exception {
        doGet(loginAs(OWNER, "owner123"), "/api/v1/shops/" + kikuyu.getId() + "/cashiers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Otieno"));
    }

    @Test
    void ownerAssignsExistingCashierToSecondShop() throws Exception {
        MockHttpSession ownerSession = loginAs(OWNER, "owner123");
        MockHttpSession cashierSession = loginAs(OTIENO, "cashier123");

        doPost(ownerSession, "/api/v1/shops/" + limuru.getId() + "/cashiers/" + otieno.getId(), null)
                .andExpect(status().isNoContent());

        doGet(cashierSession, "/api/v1/shops").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void cannotAssignSomeoneFromAnotherBusiness() throws Exception {
        doPost(loginAs(OWNER, "owner123"),
                "/api/v1/shops/" + limuru.getId() + "/cashiers/" + rivalOwner.getId(), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void removingACashierTakesEffectImmediatelyEvenMidSession() throws Exception {
        MockHttpSession ownerSession = loginAs(OWNER, "owner123");
        MockHttpSession cashierSession = loginAs(OTIENO, "cashier123");
        doGet(cashierSession, "/api/v1/shops/" + kikuyu.getId()).andExpect(status().isOk());

        doDelete(ownerSession, "/api/v1/shops/" + kikuyu.getId() + "/cashiers/" + otieno.getId())
                .andExpect(status().isNoContent());

        doGet(cashierSession, "/api/v1/shops/" + kikuyu.getId()).andExpect(status().isForbidden());
    }
}