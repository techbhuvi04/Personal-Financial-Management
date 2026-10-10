package com.example.finance.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies the opt-in cookie-to-header CSRF protection.
 */
@TestPropertySource(properties = "app.security.csrf-enabled=true")
class CsrfEnabledIntegrationTest extends IntegrationTestBase {

    @Test
    void registerAndLoginDoNotRequireToken() throws Exception {
        MockHttpSession session = newUserSession();
        getAs(session, "/api/categories").andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestWithoutTokenIsRejected() throws Exception {
        MockHttpSession session = newUserSession();

        postJson(session, "/api/categories", Map.of("name", "Gifts", "type", "EXPENSE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Missing or invalid CSRF token"));
    }

    @Test
    void stateChangingRequestWithEchoedTokenSucceeds() throws Exception {
        MockHttpSession session = newUserSession();
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfEnabled").value(true))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        JsonNode csrf = body(csrfResult);
        Cookie tokenCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/categories").session(session).cookie(tokenCookie)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Gifts", "type", "EXPENSE"))))
                .andExpect(status().isCreated());
    }
}
