package com.example.finance.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Goal endpoints with the business clock fixed at 2026-10-10.
 */
class GoalIntegrationTest extends IntegrationTestBase {

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = newUserSession();
    }

    private long createGoal(MockHttpSession owner, String target, String targetDate, String startDate)
            throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("goalName", "Emergency Fund",
                "targetAmount", new BigDecimal(target), "targetDate", targetDate));
        if (startDate != null) {
            request.put("startDate", startDate);
        }
        MvcResult result = postJson(owner, "/api/goals", request).andExpect(status().isCreated()).andReturn();
        return body(result).get("id").asLong();
    }

    @Test
    void createComputesProgressFromIncomeMinusExpensesSinceStartDate() throws Exception {
        createTransaction(session, "9999", "2025-12-31", "Salary");
        createTransaction(session, "3000", "2026-01-01", "Salary");
        createTransaction(session, "2000", "2026-05-01", "Rent");

        postJson(session, "/api/goals", Map.of("goalName", "Emergency Fund", "targetAmount", 5000.00,
                "targetDate", "2027-01-01", "startDate", "2026-01-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.goalName").value("Emergency Fund"))
                .andExpect(jsonPath("$.targetDate").value("2027-01-01"))
                .andExpect(jsonPath("$.startDate").value("2026-01-01"))
                .andExpect(content().string(containsString("\"targetAmount\":5000.00")))
                .andExpect(content().string(containsString("\"currentProgress\":1000.00")))
                .andExpect(content().string(containsString("\"progressPercentage\":20.0,")))
                .andExpect(content().string(containsString("\"remainingAmount\":4000.00")));
    }

    @Test
    void startDateDefaultsToToday() throws Exception {
        createTransaction(session, "100", "2026-10-09", "Salary");
        createTransaction(session, "40", "2026-10-10", "Salary");

        postJson(session, "/api/goals", Map.of("goalName", "Trip", "targetAmount", 400, "targetDate", "2026-12-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-10-10"))
                .andExpect(content().string(containsString("\"currentProgress\":40.00")))
                .andExpect(content().string(containsString("\"progressPercentage\":10.0,")));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource(value = {
            "targetDate,2026-10-10,Target date must be in the future",
            "targetDate,2020-01-01,Target date must be in the future",
            "startDate,2026-10-11,Start date cannot be in the future",
            "targetAmount,0,Target amount must be greater than zero",
            "targetAmount,-1,Target amount must be greater than zero",
            "targetAmount,1.234,Target amount must have at most",
            "goalName,'   ',Goal name is required",
            "targetDate,NULL,Target date is required",
            "targetAmount,NULL,Target amount is required"}, nullValues = "NULL")
    void createWithInvalidInputReturns400(String field, String value, String message) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("goalName", "Goal", "targetAmount", 100,
                "targetDate", "2027-01-01"));
        request.put(field, field.equals("targetAmount") && value != null ? new BigDecimal(value) : value);

        postJson(session, "/api/goals", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(message)));
    }

    @Test
    void createWithTooLongNameReturns400() throws Exception {
        postJson(session, "/api/goals", Map.of("goalName", "g".repeat(101), "targetAmount", 1,
                "targetDate", "2027-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsOnlyOwnGoalsEachWithIndependentProgress() throws Exception {
        createTransaction(session, "1000", "2026-01-15", "Salary");
        createTransaction(session, "500", "2026-06-15", "Salary");
        createGoal(session, "1000", "2027-01-01", "2026-01-01");
        createGoal(session, "1000", "2027-01-01", "2026-06-01");
        createGoal(newUserSession(), "1000", "2027-01-01", "2026-01-01");

        getAs(session, "/api/goals")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals", hasSize(2)))
                .andExpect(jsonPath("$.goals[0].currentProgress").value(1500.0))
                .andExpect(jsonPath("$.goals[0].progressPercentage").value(150.0))
                .andExpect(jsonPath("$.goals[0].remainingAmount").value(-500.0))
                .andExpect(jsonPath("$.goals[1].currentProgress").value(500.0))
                .andExpect(jsonPath("$.goals[1].progressPercentage").value(50.0));
    }

    @Test
    void listWithNoGoalsReturnsEmptyWrapper() throws Exception {
        getAs(session, "/api/goals").andExpect(status().isOk()).andExpect(jsonPath("$.goals", hasSize(0)));
    }

    @Test
    void negativeProgressIsReportedUncapped() throws Exception {
        createTransaction(session, "100", "2026-02-01", "Salary");
        createTransaction(session, "350", "2026-02-02", "Food");
        long id = createGoal(session, "1000", "2027-01-01", "2026-01-01");

        getAs(session, "/api/goals/" + id)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"currentProgress\":-250.00")))
                .andExpect(content().string(containsString("\"progressPercentage\":-25.0,")))
                .andExpect(content().string(containsString("\"remainingAmount\":1250.00")));
    }

    @Test
    void transactionUpdatesAndDeletionsAreReflectedInProgress() throws Exception {
        long salary = createTransaction(session, "1000", "2026-02-01", "Salary");
        long food = createTransaction(session, "200", "2026-02-02", "Food");
        long id = createGoal(session, "3000", "2027-01-01", "2026-01-01");

        putJson(session, "/api/transactions/" + salary, Map.of("amount", 2000)).andExpect(status().isOk());
        getAs(session, "/api/goals/" + id).andExpect(jsonPath("$.currentProgress").value(1800.0));

        deleteAs(session, "/api/transactions/" + food).andExpect(status().isOk());
        getAs(session, "/api/goals/" + id)
                .andExpect(jsonPath("$.currentProgress").value(2000.0))
                .andExpect(content().string(containsString("\"progressPercentage\":66.67")))
                .andExpect(jsonPath("$.remainingAmount").value(1000.0));
    }

    @Test
    void getAnotherUsersGoalIsForbiddenAndMissingGoalIsNotFound() throws Exception {
        long id = createGoal(session, "100", "2027-01-01", null);
        MockHttpSession intruder = newUserSession();

        getAs(intruder, "/api/goals/" + id).andExpect(status().isForbidden());
        putJson(intruder, "/api/goals/" + id, Map.of("targetAmount", 1)).andExpect(status().isForbidden());
        deleteAs(intruder, "/api/goals/" + id).andExpect(status().isForbidden());
        getAs(session, "/api/goals/987654").andExpect(status().isNotFound());
        getAs(session, "/api/goals/not-a-number").andExpect(status().isBadRequest());
    }

    @Test
    void updateTargetAmountAndDateRecalculatesImmediately() throws Exception {
        createTransaction(session, "1000", "2026-02-01", "Salary");
        long id = createGoal(session, "5000", "2027-01-01", "2026-01-01");

        putJson(session, "/api/goals/" + id, Map.of("targetAmount", 6000.00, "targetDate", "2027-02-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalName").value("Emergency Fund"))
                .andExpect(jsonPath("$.startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.targetDate").value("2027-02-01"))
                .andExpect(content().string(containsString("\"targetAmount\":6000.00")))
                .andExpect(content().string(containsString("\"progressPercentage\":16.67")))
                .andExpect(content().string(containsString("\"remainingAmount\":5000.00")));
    }

    @Test
    void updateWithNothingToChangeOrOnlyReadOnlyFieldsIsANoOp() throws Exception {
        long id = createGoal(session, "5000", "2027-01-01", null);

        putJson(session, "/api/goals/" + id, Map.of()).andExpect(status().isOk());
        putJson(session, "/api/goals/" + id, Map.of("startDate", "2020-01-01", "goalName", "Renamed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-10-10"))
                .andExpect(jsonPath("$.goalName").value("Emergency Fund"))
                .andExpect(jsonPath("$.targetAmount").value(5000.0));
    }

    @Test
    void invalidUpdatesReturn400AndChangeNothing() throws Exception {
        long id = createGoal(session, "5000", "2027-01-01", null);

        putJson(session, "/api/goals/" + id, Map.of("targetAmount", 10, "targetDate", "2026-10-10"))
                .andExpect(status().isBadRequest());
        putJson(session, "/api/goals/" + id, Map.of("targetAmount", -10)).andExpect(status().isBadRequest());

        getAs(session, "/api/goals/" + id)
                .andExpect(jsonPath("$.targetAmount").value(5000.0))
                .andExpect(jsonPath("$.targetDate").value("2027-01-01"));
    }

    @Test
    void deleteGoalKeepsTransactions() throws Exception {
        createTransaction(session, "1000", "2026-02-01", "Salary");
        long id = createGoal(session, "5000", "2027-01-01", "2026-01-01");

        deleteAs(session, "/api/goals/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Goal deleted successfully"));
        getAs(session, "/api/goals/" + id).andExpect(status().isNotFound());
        getAs(session, "/api/transactions").andExpect(jsonPath("$.transactions", hasSize(1)));
    }
}
