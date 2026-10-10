package com.example.finance.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class ErrorHandlingIntegrationTest extends IntegrationTestBase {

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = newUserSession();
    }

    @Test
    void malformedJsonReturns400WithoutInternals() throws Exception {
        mockMvc.perform(post("/api/transactions").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10, "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"))
                .andExpect(jsonPath("$.path").value("/api/transactions"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void wrongJsonTypeReturns400NamingTheField() throws Exception {
        mockMvc.perform(post("/api/transactions").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": \"lots\", \"date\": \"2024-01-01\", \"category\": \"Food\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'lots' for field 'amount': expected a number"));
        mockMvc.perform(post("/api/transactions").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1, \"date\": [2024], \"category\": \"Food\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("field 'date'")));
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/goals").session(session).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is required"));
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/categories").session(session).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Content type must be application/json"));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(patch("/api/transactions/1").session(session))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownEndpointReturns404ForAuthenticatedUser() throws Exception {
        getAs(session, "/api/unknown")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Endpoint not found"));
    }

    @Test
    void oversizedBodyReturns413BeforeParsing() throws Exception {
        mockMvc.perform(post("/api/transactions").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + "x".repeat(20_000) + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void unacceptableResponseTypeReturns406() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/categories")
                        .session(session).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }
}
