package com.dukaclose.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
@ActiveProfiles("test")

public abstract class AbstractApiTest {

    @Autowired protected WebApplicationContext context;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected UserRepository users;
    @Autowired protected BusinessRepository businesses;
    @Autowired protected ShopRepository shops;
    @Autowired protected BusinessMembershipRepository businessMemberships;
    @Autowired protected ShopMembershipRepository shopMemberships;

    protected MockMvc mvc;

    @BeforeEach
    protected void buildMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected User newUser(String name, String phone, String password) {
        return users.save(new User(name, phone, passwordEncoder.encode(password)));
    }

    protected MockHttpSession loginAs(String phone, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(phone, password)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("login as " + phone).isEqualTo(200);
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected ResultActions doGet(MockHttpSession session, String url) throws Exception {
        return send(get(url), session);
    }

    protected ResultActions doPost(MockHttpSession session, String url, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(url).with(csrf());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return send(request, session);
    }

    protected ResultActions doDelete(MockHttpSession session, String url) throws Exception {
        return send(delete(url).with(csrf()), session);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        if (session != null) {
            request.session(session);
        }
        return mvc.perform(request);
    }
    protected ResultActions doPut(MockHttpSession session, String url, String json) throws Exception {
        MockHttpServletRequestBuilder request = put(url).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
        return send(request, session);
    }
}