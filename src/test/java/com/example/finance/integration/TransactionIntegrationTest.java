package com.example.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;

class TransactionIntegrationTest extends IntegrationTestBase {

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = newUserSession();
    }

    @Test
    void createReturns201WithContractFieldsAndDerivedType() throws Exception {
        postJson(session, "/api/transactions", Map.of("amount", new BigDecimal("50000.00"), "date", "2024-01-15",
                "category", "Salary", "description", "January Salary"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.date").value("2024-01-15"))
                .andExpect(jsonPath("$.category").value("Salary"))
                .andExpect(jsonPath("$.description").value("January Salary"))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(content().string(containsString("\"amount\":50000.00")));
    }

    @Test
    void createAcceptsCaseInsensitiveCategoryTodayAndMatchingTypeWithoutDescription() throws Exception {
        postJson(session, "/api/transactions", Map.of("amount", 12, "date", "2026-10-10", "category", "  food ",
                "type", "EXPENSE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(content().string(containsString("\"amount\":12.00")));
    }

    static Stream<Arguments> invalidCreateRequests() {
        return Stream.of(
                Arguments.of("amount", null, "Amount is required"),
                Arguments.of("amount", 0, "Amount must be greater than zero"),
                Arguments.of("amount", -10.5, "Amount must be greater than zero"),
                Arguments.of("amount", new BigDecimal("10.001"), "Amount must have at most 13 integer digits"),
                Arguments.of("amount", new BigDecimal("12345678901234"), "Amount must have at most 13 integer digits"),
                Arguments.of("date", null, "Date is required"),
                Arguments.of("category", "", "Category is required"),
                Arguments.of("category", "x".repeat(51), "Category must be at most 50 characters"),
                Arguments.of("description", "d".repeat(256), "Description must be at most 255 characters"));
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("invalidCreateRequests")
    void createWithInvalidFieldReturns400(String field, Object value, String message) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("amount", 10, "date", "2024-01-15", "category", "Food"));
        request.put(field, value);

        postJson(session, "/api/transactions", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).value(org.hamcrest.Matchers.startsWith(message)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-30", "15-01-2024", "2024/01/15", "2024-1-5", "yesterday", "2024-01-15T10:00:00"})
    void createWithMalformedDateReturns400(String date) throws Exception {
        postJson(session, "/api/transactions", Map.of("amount", 10, "date", date, "category", "Food"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("expected a date in yyyy-MM-dd format")));
    }

    @Test
    void createWithFutureDateReturns400() throws Exception {
        postJson(session, "/api/transactions", Map.of("amount", 10, "date", "2026-10-11", "category", "Food"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction date cannot be in the future"));
    }

    @Test
    void createWithUnknownCategoryReturns400() throws Exception {
        postJson(session, "/api/transactions", Map.of("amount", 10, "date", "2024-01-15", "category", "Yachts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category 'Yachts' does not exist"));
    }

    @Test
    void createWithAnotherUsersCustomCategoryReturns400() throws Exception {
        MockHttpSession other = newUserSession();
        createCategory(other, "Crypto", "INCOME");

        postJson(session, "/api/transactions", Map.of("amount", 10, "date", "2024-01-15", "category", "Crypto"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithTypeConflictingWithCategoryReturns400() throws Exception {
        postJson(session, "/api/transactions", Map.of("amount", 10, "date", "2024-01-15", "category", "Salary",
                "type", "EXPENSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("does not match category 'Salary'")));
    }

    @Test
    void createWithInvalidTypeValueReturns400() throws Exception {
        postJson(session, "/api/transactions", Map.of("amount", 10, "date", "2024-01-15", "category", "Salary",
                "type", "TRANSFER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("expected one of [INCOME, EXPENSE]")));
    }

    @Test
    void listReturnsOnlyOwnTransactionsNewestFirstWithIdTieBreak() throws Exception {
        long older = createTransaction(session, "10", "2024-01-01", "Food");
        long sameDayFirst = createTransaction(session, "20", "2024-03-01", "Food");
        long sameDaySecond = createTransaction(session, "30", "2024-03-01", "Rent");
        createTransaction(newUserSession(), "999", "2024-02-01", "Food");

        getAs(session, "/api/transactions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(3)))
                .andExpect(jsonPath("$.transactions[*].id").value(contains(
                        (int) sameDaySecond, (int) sameDayFirst, (int) older)));
    }

    @Test
    void listWithNoTransactionsReturnsEmptyWrapper() throws Exception {
        getAs(session, "/api/transactions")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"transactions\":[]}"));
    }

    @Test
    void listFiltersAreInclusiveAndCombinable() throws Exception {
        createCategory(session, "Freelance", "INCOME");
        createTransaction(session, "1", "2024-01-01", "Salary");
        createTransaction(session, "2", "2024-01-15", "Food");
        createTransaction(session, "3", "2024-01-31", "Freelance");
        createTransaction(session, "4", "2024-02-01", "Food");
        Long foodId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE normalized_name = 'food'",
                Long.class);

        getAs(session, "/api/transactions?startDate=2024-01-01&endDate=2024-01-31")
                .andExpect(jsonPath("$.transactions[*].amount").value(contains(3.0, 2.0, 1.0)));
        getAs(session, "/api/transactions?categoryId=" + foodId)
                .andExpect(jsonPath("$.transactions[*].amount").value(contains(4.0, 2.0)));
        getAs(session, "/api/transactions?category=FREELANCE")
                .andExpect(jsonPath("$.transactions[*].amount").value(contains(3.0)));
        getAs(session, "/api/transactions?type=INCOME")
                .andExpect(jsonPath("$.transactions[*].amount").value(contains(3.0, 1.0)));
        getAs(session, "/api/transactions?type=expense&startDate=2024-01-02&endDate=2024-01-31&categoryId=" + foodId)
                .andExpect(jsonPath("$.transactions[*].amount").value(contains(2.0)));
        getAs(session, "/api/transactions?categoryId=999999")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(0)));
    }

    @Test
    void listRejectsInvalidQueryParameters() throws Exception {
        getAs(session, "/api/transactions?startDate=2024-02-01&endDate=2024-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate must be on or before endDate"));
        getAs(session, "/api/transactions?startDate=2024-13-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("parameter 'startDate'")));
        getAs(session, "/api/transactions?type=SAVINGS").andExpect(status().isBadRequest());
        getAs(session, "/api/transactions?categoryId=abc").andExpect(status().isBadRequest());
        getAs(session, "/api/transactions?categoryId=0").andExpect(status().isBadRequest());
    }

    @Test
    void updateChangesPermittedFieldsAndKeepsDate() throws Exception {
        long id = createTransaction(session, "50000", "2024-01-15", "Salary");

        putJson(session, "/api/transactions/" + id, Map.of("amount", new BigDecimal("60000.00"),
                "description", "Updated January Salary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.date").value("2024-01-15"))
                .andExpect(jsonPath("$.category").value("Salary"))
                .andExpect(jsonPath("$.description").value("Updated January Salary"))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(content().string(containsString("\"amount\":60000.00")));
    }

    @Test
    void updateCategoryChangesDerivedTypeAndEmptyDescriptionClearsIt() throws Exception {
        long id = createTransaction(session, "100", "2024-01-15", "Salary");

        putJson(session, "/api/transactions/" + id, Map.of("category", "Rent", "description", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Rent"))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void updateRejectsDateChangeButAcceptsUnchangedDate() throws Exception {
        long id = createTransaction(session, "100", "2024-01-15", "Food");

        putJson(session, "/api/transactions/" + id, Map.of("amount", 5, "date", "2024-01-16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction date cannot be changed"));
        putJson(session, "/api/transactions/" + id, Map.of("amount", 5, "date", "2024-01-15"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT transaction_date FROM transactions WHERE id = ?",
                java.sql.Date.class, id).toLocalDate()).isEqualTo("2024-01-15");
    }

    @Test
    void updateValidationFailuresDoNotPersistAnything() throws Exception {
        long id = createTransaction(session, "100", "2024-01-15", "Food");

        putJson(session, "/api/transactions/" + id, Map.of()).andExpect(status().isBadRequest());
        putJson(session, "/api/transactions/" + id, Map.of("amount", 0)).andExpect(status().isBadRequest());
        putJson(session, "/api/transactions/" + id, Map.of("amount", 1, "category", "Unknown"))
                .andExpect(status().isBadRequest());
        putJson(session, "/api/transactions/" + id, Map.of("amount", 1, "category", "Salary", "type", "EXPENSE"))
                .andExpect(status().isBadRequest());
        putJson(session, "/api/transactions/" + id, Map.of("category", "   ")).andExpect(status().isBadRequest());

        getAs(session, "/api/transactions")
                .andExpect(jsonPath("$.transactions[0].amount").value(100.0))
                .andExpect(jsonPath("$.transactions[0].category").value("Food"));
    }

    @Test
    void updateOrDeleteOfAnotherUsersTransactionIsNotFound() throws Exception {
        long id = createTransaction(session, "100", "2024-01-15", "Food");
        MockHttpSession intruder = newUserSession();

        putJson(intruder, "/api/transactions/" + id, Map.of("amount", 1)).andExpect(status().isNotFound());
        deleteAs(intruder, "/api/transactions/" + id).andExpect(status().isNotFound());

        getAs(session, "/api/transactions").andExpect(jsonPath("$.transactions[0].amount").value(100.0));
    }

    @Test
    void updateMissingTransactionReturns404() throws Exception {
        putJson(session, "/api/transactions/987654", Map.of("amount", 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction 987654 not found"));
    }

    @Test
    void deleteReturnsMessageAndRepeatedDeleteIs404() throws Exception {
        long id = createTransaction(session, "100", "2024-01-15", "Food");

        deleteAs(session, "/api/transactions/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transaction deleted successfully"));
        deleteAs(session, "/api/transactions/" + id).andExpect(status().isNotFound());
        getAs(session, "/api/transactions").andExpect(jsonPath("$.transactions", hasSize(0)));
    }
}
