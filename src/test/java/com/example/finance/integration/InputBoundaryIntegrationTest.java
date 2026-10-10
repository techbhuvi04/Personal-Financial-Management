package com.example.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;

/**
 * Boundary inputs: password byte limits, supported date range, mass assignment, persistence of rejected requests
 * and monetary precision limits.
 */
class InputBoundaryIntegrationTest extends IntegrationTestBase {

    /** 3 ASCII bytes + 35 two-byte characters = 73 UTF-8 bytes in only 38 characters. */
    private static final String PASSWORD_73_BYTES = "Aa1" + "é".repeat(35);
    /** 3 + 68 + 1 = exactly 72 UTF-8 bytes. */
    private static final String PASSWORD_72_BYTES = "Aa1" + "é".repeat(34) + "x";

    @Test
    void registrationRejectsPasswordsLongerThan72Utf8Bytes() throws Exception {
        assertThat(PASSWORD_73_BYTES.getBytes(StandardCharsets.UTF_8)).hasSize(73);

        postJson(null, "/api/auth/register", Map.of("username", uniqueEmail(), "password", PASSWORD_73_BYTES,
                "fullName", "Long Password", "phoneNumber", "+1234567890"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value(containsString("72 bytes")));
    }

    @Test
    void loginDoesNotAcceptPasswordsThatOnlyShareTheFirst72Bytes() throws Exception {
        assertThat(PASSWORD_72_BYTES.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        String email = uniqueEmail();
        postJson(null, "/api/auth/register", Map.of("username", email, "password", PASSWORD_72_BYTES,
                "fullName", "Long Password", "phoneNumber", "+1234567890"))
                .andExpect(status().isCreated());

        postJson(null, "/api/auth/login", Map.of("username", email, "password", PASSWORD_72_BYTES))
                .andExpect(status().isOk());
        postJson(null, "/api/auth/login", Map.of("username", email, "password", PASSWORD_72_BYTES + "Q1"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1899-12-31", "0001-01-01", "-2024-01-15"})
    void transactionDatesBefore1900AreRejectedAndNotPersisted(String date) throws Exception {
        MockHttpSession session = newUserSession();

        postJson(session, "/api/transactions", Map.of("amount", 1, "date", date, "category", "Food"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("1900-01-01")));
        assertThat(transactionCount()).isZero();
    }

    @Test
    void earliestSupportedTransactionDateIsAccepted() throws Exception {
        MockHttpSession session = newUserSession();

        createTransaction(session, "1", "1900-01-01", "Food");
        getAs(session, "/api/reports/monthly/1900/1").andExpect(jsonPath("$.totalExpenses.Food").value(1.0));
    }

    @Test
    void goalDatesOutsideTheSupportedRangeAreRejected() throws Exception {
        MockHttpSession session = newUserSession();

        postJson(session, "/api/goals", Map.of("goalName", "Far", "targetAmount", 1, "targetDate", "+10000-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("9999-12-31")));
        postJson(session, "/api/goals", Map.of("goalName", "Far", "targetAmount", 1,
                "targetDate", "+999999999-12-31"))
                .andExpect(status().isBadRequest());
        postJson(session, "/api/goals", Map.of("goalName", "Old", "targetAmount", 1, "targetDate", "2027-01-01",
                "startDate", "1899-12-31"))
                .andExpect(status().isBadRequest());
        postJson(session, "/api/goals", Map.of("goalName", "Edge", "targetAmount", 1, "targetDate", "9999-12-31",
                "startDate", "1900-01-01"))
                .andExpect(status().isCreated());
        long id = body(postJson(session, "/api/goals", Map.of("goalName", "Edge", "targetAmount", 1,
                "targetDate", "2027-01-01")).andReturn()).get("id").asLong();
        putJson(session, "/api/goals/" + id, Map.of("targetDate", "+10000-01-01"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"startDate=%2B999999999-12-31", "endDate=-999999999-01-01", "startDate=1899-12-31",
            "endDate=%2B10000-01-01"})
    void listFilterDatesOutsideTheSupportedRangeAreRejected(String query) throws Exception {
        getAs(newUserSession(), "/api/transactions?" + query)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectedCreateRequestsPersistNothing() throws Exception {
        MockHttpSession session = newUserSession();
        Map<String, Object> valid = Map.of("amount", 10, "date", "2024-01-01", "category", "Food");

        for (Map.Entry<String, Object> override : Map.<String, Object>of("amount", -1, "date", "2030-01-01",
                "category", "Unknown", "type", "INCOME").entrySet()) {
            Map<String, Object> request = new HashMap<>(valid);
            request.put(override.getKey(), override.getValue());
            postJson(session, "/api/transactions", request).andExpect(status().isBadRequest());
        }
        postJson(session, "/api/goals", Map.of("goalName", "x", "targetAmount", 0, "targetDate", "2027-01-01"))
                .andExpect(status().isBadRequest());
        postJson(session, "/api/categories", Map.of("name", "Food", "type", "EXPENSE"))
                .andExpect(status().isConflict());

        assertThat(transactionCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM savings_goals", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE is_default = FALSE",
                Integer.class)).isZero();
    }

    @Test
    void clientSuppliedOwnerAndIdFieldsAreIgnored() throws Exception {
        MockHttpSession alice = newUserSession();
        String bobEmail = uniqueEmail();
        register(bobEmail);
        MockHttpSession bob = login(bobEmail);
        Long bobId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, bobEmail);

        long id = body(postJson(alice, "/api/transactions", Map.of("amount", 10, "date", "2024-01-01",
                "category", "Food", "userId", bobId, "user", Map.of("id", bobId), "id", 424242))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        putJson(alice, "/api/transactions/" + id, Map.of("amount", 11, "userId", bobId, "id", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(id).isNotEqualTo(424242L);
        assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM transactions WHERE id = ?", Long.class, id))
                .isNotEqualTo(bobId);
        getAs(bob, "/api/transactions").andExpect(jsonPath("$.transactions").isEmpty());
    }

    @Test
    void amountsAtThePrecisionLimitAreStoredAndAggregatedExactly() throws Exception {
        MockHttpSession session = newUserSession();

        postJson(session, "/api/transactions", Map.of("amount", new java.math.BigDecimal("9999999999999.99"),
                "date", "2024-03-01", "category", "Rent"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"amount\":9999999999999.99")));
        createTransaction(session, "9999999999999.99", "2024-03-02", "Rent");
        postJson(session, "/api/transactions", Map.of("amount", new java.math.BigDecimal("99999999999999.99"),
                "date", "2024-03-01", "category", "Rent"))
                .andExpect(status().isBadRequest());

        getAs(session, "/api/reports/monthly/2024/3")
                .andExpect(content().string(containsString("\"Rent\":19999999999999.98")))
                .andExpect(content().string(containsString("\"netSavings\":-19999999999999.98}")));
    }

    @Test
    void blankDescriptionsAreStoredAsAbsentAndTextIsTrimmed() throws Exception {
        MockHttpSession session = newUserSession();

        postJson(session, "/api/transactions", Map.of("amount", 1, "date", "2024-01-01", "category", "Food",
                "description", ""))
                .andExpect(jsonPath("$.description").doesNotExist());
        postJson(session, "/api/transactions", Map.of("amount", 1, "date", "2024-01-01", "category", "Food",
                "description", "   "))
                .andExpect(jsonPath("$.description").doesNotExist());
        postJson(session, "/api/transactions", Map.of("amount", 1, "date", "2024-01-01", "category", "Food",
                "description", "  lunch  "))
                .andExpect(jsonPath("$.description").value("lunch"));
    }

    private int transactionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);
    }
}
