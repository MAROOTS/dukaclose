package com.dukaclose.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dukaclose.api.business.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AuthApiTest {

    @Autowired WebApplicationContext context;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired ShopRepository shops;
    @Autowired BusinessMembershipRepository businessMemberships;
    @Autowired ShopMembershipRepository shopMemberships;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User newUser(String name, String phone, String password) {
        return users.save(new User(name, phone, passwordEncoder.encode(password)));
    }

    private MvcResult login(String identifier, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)))
                .andReturn();
    }

    private MockHttpSession sessionOf(MvcResult loginResult) {
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).as("login should create a session").isNotNull();
        return session;
    }

    @Test
    void correctCredentialsLogInAndReturnProfile() throws Exception {
        newUser("Njeri", "+254700000001", "secret123");

        MvcResult result = login("+254700000001", "secret123");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        mvc.perform(get("/api/v1/me").session(sessionOf(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("Njeri"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        newUser("Njeri", "+254700000001", "secret123");

        for (MvcResult r : new MvcResult[]{login("+254700000001", "wrong"), login("+254799999999", "secret123")}) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(r.getResponse().getContentAsString()).contains("Invalid phone or password");
        }
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        User user = newUser("Njeri", "+254700000001", "secret123");
        user.deactivate();
        users.saveAndFlush(user);

        assertThat(login("+254700000001", "secret123").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void meRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        newUser("Njeri", "+254700000001", "secret123");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"+254700000001\",\"password\":\"secret123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        newUser("Njeri", "+254700000001", "secret123");
        MockHttpSession session = sessionOf(login("+254700000001", "secret123"));

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerSeesAllShopsAndCashierOnlyTheirOwn() throws Exception {
        Business business = businesses.save(new Business("Mama Njeri Stores"));
        Shop kikuyu = shops.save(new Shop(business.getId(), "Kikuyu"));
        shops.save(new Shop(business.getId(), "Limuru"));
        User owner = newUser("Njeri", "+254700000001", "owner123");
        User cashier = newUser("Otieno", "+254700000002", "cashier123");
        businessMemberships.save(new BusinessMembership(business.getId(), owner.getId(), Role.OWNER));
        shopMemberships.save(new ShopMembership(kikuyu.getId(), cashier.getId(), Role.CASHIER));

        mvc.perform(get("/api/v1/me").session(sessionOf(login("+254700000001", "owner123"))))
                .andExpect(jsonPath("$.shops.length()").value(2))
                .andExpect(jsonPath("$.businesses.length()").value(1));

        mvc.perform(get("/api/v1/me").session(sessionOf(login("+254700000002", "cashier123"))))
                .andExpect(jsonPath("$.shops.length()").value(1))
                .andExpect(jsonPath("$.shops[0].name").value("Kikuyu"))
                .andExpect(jsonPath("$.shops[0].role").value("CASHIER"))
                .andExpect(jsonPath("$.businesses.length()").value(0));
    }
}