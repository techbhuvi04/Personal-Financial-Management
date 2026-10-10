package com.example.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registerReturns201WithUserIdAndStoresBcryptHash() throws Exception {
        String email = uniqueEmail();

        register(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.userId").isNumber());

        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE username = ?",
                String.class, email);
        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void registerNormalizesEmailAndPhone() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "  Mixed.Case@Example.COM ", "password", PASSWORD,
                                "fullName", " Jane O'Neil ", "phoneNumber", "+1 (234) 567-890"))))
                .andExpect(status().isCreated());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT username, full_name, phone_number FROM users WHERE username = 'mixed.case@example.com'");
        assertThat(row.get("full_name")).isEqualTo("Jane O'Neil");
        assertThat(row.get("phone_number")).isEqualTo("+1234567890");
        login("MIXED.case@example.com");
    }

    @Test
    void duplicateRegistrationIsCaseInsensitiveConflict() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        register(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with this username already exists"));
    }

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("username", null, "Username is required"),
                Arguments.of("username", "not-an-email", "Username must be a valid email address"),
                Arguments.of("username", "user@localhost", "Username must be a valid email address"),
                Arguments.of("password", null, "Password is required"),
                Arguments.of("password", "short1", "Password must be between 8 and 72 characters"),
                Arguments.of("password", "allletters", "Password must contain at least one letter and one digit"),
                Arguments.of("password", "12345678", "Password must contain at least one letter and one digit"),
                Arguments.of("password", "a1".repeat(37), "Password must be between 8 and 72 characters"),
                Arguments.of("fullName", "   ", "Full name is required"),
                Arguments.of("fullName", "R2D2", "Full name may only contain"),
                Arguments.of("fullName", "a".repeat(101), "Full name must be at most 100 characters"),
                Arguments.of("phoneNumber", null, "Phone number is required"),
                Arguments.of("phoneNumber", "12ab", "Phone number must contain 7 to 15 digits"),
                Arguments.of("phoneNumber", "+0123456", "Phone number must contain 7 to 15 digits"),
                Arguments.of("phoneNumber", "1234567890123456", "Phone number must contain 7 to 15 digits"));
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("invalidRegistrations")
    void invalidRegistrationReturns400WithFieldError(String field, String value, String expectedMessage)
            throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("username", uniqueEmail(), "password", PASSWORD,
                "fullName", "Valid Name", "phoneNumber", "+1234567890"));
        request.put(field, value);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).value(org.hamcrest.Matchers.startsWith(expectedMessage)));
    }

    @Test
    void registerWithoutBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is required"));
    }

    @Test
    void loginEstablishesSessionUsableForProtectedEndpoints() throws Exception {
        MockHttpSession session = newUserSession();

        assertThat(session).isNotNull();
        getAs(session, "/api/categories").andExpect(status().isOk());
    }

    @Test
    void loginResponseHasExpectedMessage() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"));
    }

    @Test
    void loginChangesExistingSessionIdToPreventFixation() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession preLoginSession = new MockHttpSession();
        String originalId = preLoginSession.getId();

        MvcResult result = mockMvc.perform(post("/api/auth/login").session(preLoginSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(originalId);
    }

    @Test
    void wrongPasswordAndUnknownUserAreIndistinguishable401() throws Exception {
        String email = uniqueEmail();
        register(email);

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", email, "password", "wrongpass1"))))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknownUser = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", uniqueEmail(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(body(wrongPassword).get("message")).isEqualTo(body(unknownUser).get("message"));
        assertThat(wrongPassword.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"username\":\"a@b.com\"}", "{\"password\":\"x\"}"})
    void loginWithMissingFieldsReturns400(String requestBody) throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutInvalidatesSessionAndClearsCookie() throws Exception {
        MockHttpSession session = newUserSession();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"))
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(session.isInvalid()).isTrue();
        getAs(session, "/api/transactions").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutSessionReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpointsRequireAuthenticationWithJsonError() throws Exception {
        for (String path : new String[]{"/api/transactions", "/api/categories", "/api/goals", "/api/goals/1",
                "/api/reports/monthly/2024/1", "/api/reports/yearly/2024"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.path").value(path));
        }
    }

    @Test
    void csrfEndpointReportsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfEnabled").value(false))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
