package com.example.finance.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Full-stack test base: real security filter chain, services, Flyway-migrated H2 database and a fixed clock.
 * Each test registers its own users and all user data is removed afterwards, so tests are order-independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", email, "password", PASSWORD, "fullName", "Test User",
                        "phoneNumber", "+1234567890"))));
    }

    /** Registers a fresh user and returns an authenticated session. */
    protected MockHttpSession newUserSession() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        return login(email);
    }

    protected MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected ResultActions postJson(MockHttpSession session, String path, Object body) throws Exception {
        return mockMvc.perform(post(path).session(session).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions putJson(MockHttpSession session, String path, Object body) throws Exception {
        return mockMvc.perform(put(path).session(session).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions getAs(MockHttpSession session, String path) throws Exception {
        return mockMvc.perform(get(path).session(session));
    }

    protected ResultActions deleteAs(MockHttpSession session, String path) throws Exception {
        return mockMvc.perform(delete(path).session(session));
    }

    /** Creates a transaction and returns its id. */
    protected long createTransaction(MockHttpSession session, String amount, String date, String category)
            throws Exception {
        MvcResult result = postJson(session, "/api/transactions",
                Map.of("amount", new java.math.BigDecimal(amount), "date", date, "category", category))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    /** Creates a custom category for the session's user. */
    protected void createCategory(MockHttpSession session, String name, String type) throws Exception {
        postJson(session, "/api/categories", Map.of("name", name, "type", type)).andExpect(status().isCreated());
    }
}
