package com.example.finance.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end financial arithmetic with fixtures whose expected results were computed by hand (business clock
 * fixed at 2026-10-10). Assertions compare the exact serialized JSON values.
 */
class FinancialCalculationIntegrationTest extends IntegrationTestBase {

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = newUserSession();
    }

    private ResultActions goal(String target, String startDate) throws Exception {
        return postJson(session, "/api/goals", Map.of("goalName", "Goal", "targetAmount", new java.math.BigDecimal(target),
                "targetDate", "2027-06-30", "startDate", startDate)).andExpect(status().isCreated());
    }

    private static org.springframework.test.web.servlet.ResultMatcher has(String fragment) {
        return content().string(containsString(fragment));
    }

    @Test
    void goalWithNoTransactionsHasZeroProgress() throws Exception {
        goal("1000", "2026-01-01")
                .andExpect(has("\"currentProgress\":0,"))
                .andExpect(has("\"progressPercentage\":0.0,"))
                .andExpect(has("\"remainingAmount\":1000.00}"));
    }

    @Test
    void incomeOnlyProgress() throws Exception {
        createTransaction(session, "250.25", "2026-03-01", "Salary");
        createTransaction(session, "249.75", "2026-03-01", "Salary");

        goal("2000", "2026-01-01")
                .andExpect(has("\"currentProgress\":500.00,"))
                .andExpect(has("\"progressPercentage\":25.0,"))
                .andExpect(has("\"remainingAmount\":1500.00}"));
    }

    @Test
    void expensesOnlyProgressIsNegativeAndUncapped() throws Exception {
        createTransaction(session, "120.50", "2026-02-01", "Food");
        createTransaction(session, "79.50", "2026-02-01", "Utilities");

        goal("400", "2026-01-01")
                .andExpect(has("\"currentProgress\":-200.00,"))
                .andExpect(has("\"progressPercentage\":-50.0,"))
                .andExpect(has("\"remainingAmount\":600.00}"));
    }

    @Test
    void startDateAndTodayAreInclusiveAndEarlierTransactionsAreExcluded() throws Exception {
        createTransaction(session, "999", "2026-04-30", "Salary");
        createTransaction(session, "100", "2026-05-01", "Salary");
        createTransaction(session, "40", "2026-10-10", "Food");

        goal("120", "2026-05-01")
                .andExpect(has("\"currentProgress\":60.00,"))
                .andExpect(has("\"progressPercentage\":50.0,"));
    }

    @Test
    void progressExactlyAtTargetAndBeyond() throws Exception {
        createTransaction(session, "300", "2026-06-01", "Salary");

        goal("300", "2026-01-01")
                .andExpect(has("\"progressPercentage\":100.0,"))
                .andExpect(has("\"remainingAmount\":0}"));
        goal("200", "2026-01-01")
                .andExpect(has("\"progressPercentage\":150.0,"))
                .andExpect(has("\"remainingAmount\":-100.00}"));
    }

    @Test
    void repeatingDecimalsRoundHalfUpToTwoPlaces() throws Exception {
        createTransaction(session, "100", "2026-06-01", "Salary");

        goal("300", "2026-01-01").andExpect(has("\"progressPercentage\":33.33,"));
        goal("150", "2026-01-01").andExpect(has("\"progressPercentage\":66.67,"));
        goal("0.07", "2026-01-01").andExpect(has("\"progressPercentage\":142857.14,"));
    }

    @Test
    void largeAmountsKeepExactPrecision() throws Exception {
        createTransaction(session, "9999999999999.99", "2026-06-01", "Salary");
        createTransaction(session, "0.01", "2026-06-02", "Food");

        goal("9999999999999.98", "2026-01-01")
                .andExpect(has("\"currentProgress\":9999999999999.98,"))
                .andExpect(has("\"progressPercentage\":100.0,"))
                .andExpect(has("\"remainingAmount\":0}"));
    }

    @Test
    void netSavingsAcrossCategoriesAndDays() throws Exception {
        createCategory(session, "Freelance", "INCOME");
        createTransaction(session, "1000", "2026-07-01", "Salary");
        createTransaction(session, "333.33", "2026-07-01", "Freelance");
        createTransaction(session, "0.10", "2026-07-02", "Food");
        createTransaction(session, "0.20", "2026-07-02", "Food");
        createTransaction(session, "0.30", "2026-07-03", "Transportation");

        goal("2000", "2026-07-01")
                .andExpect(has("\"currentProgress\":1332.73,"))
                .andExpect(has("\"progressPercentage\":66.64,"));
        getAs(session, "/api/reports/monthly/2026/7")
                .andExpect(has("\"totalIncome\":{\"Freelance\":333.33,\"Salary\":1000.00}"))
                .andExpect(has("\"totalExpenses\":{\"Food\":0.30,\"Transportation\":0.30}"))
                .andExpect(has("\"netSavings\":1332.73}"));
    }

    @Test
    void nonLeapFebruaryBoundaries() throws Exception {
        createTransaction(session, "1", "2023-01-31", "Food");
        createTransaction(session, "2", "2023-02-01", "Food");
        createTransaction(session, "3", "2023-02-28", "Food");
        createTransaction(session, "4", "2023-03-01", "Food");

        getAs(session, "/api/reports/monthly/2023/2")
                .andExpect(has("\"totalExpenses\":{\"Food\":5.00}"))
                .andExpect(has("\"netSavings\":-5.00}"));
    }

    @Test
    void incomeOnlyAndExpenseOnlyMonths() throws Exception {
        createTransaction(session, "10", "2025-01-15", "Salary");
        createTransaction(session, "7.5", "2025-02-15", "Rent");

        getAs(session, "/api/reports/monthly/2025/1")
                .andExpect(has("\"totalIncome\":{\"Salary\":10.00},\"totalExpenses\":{},\"netSavings\":10.00}"));
        getAs(session, "/api/reports/monthly/2025/2")
                .andExpect(has("\"totalIncome\":{},\"totalExpenses\":{\"Rent\":7.50},\"netSavings\":-7.50}"));
        getAs(session, "/api/reports/yearly/2025")
                .andExpect(has("\"netSavings\":2.50}"));
    }

    @Test
    void decemberAndJanuaryYearBoundaries() throws Exception {
        createTransaction(session, "1", "2024-12-31", "Salary");
        createTransaction(session, "2", "2025-01-01", "Salary");
        createTransaction(session, "4", "2025-12-31", "Salary");
        createTransaction(session, "8", "2026-01-01", "Salary");

        getAs(session, "/api/reports/monthly/2025/12").andExpect(has("\"netSavings\":4.00}"));
        getAs(session, "/api/reports/monthly/2025/1").andExpect(has("\"netSavings\":2.00}"));
        getAs(session, "/api/reports/yearly/2025").andExpect(has("\"netSavings\":6.00}"));
    }

    @Test
    void reportCategoryOrderIsCaseInsensitiveAlphabetical() throws Exception {
        createCategory(session, "apples", "EXPENSE");
        createCategory(session, "Bananas", "EXPENSE");
        createTransaction(session, "1", "2025-05-01", "Bananas");
        createTransaction(session, "1", "2025-05-01", "apples");
        createTransaction(session, "1", "2025-05-01", "Food");

        getAs(session, "/api/reports/monthly/2025/5")
                .andExpect(has("\"totalExpenses\":{\"apples\":1.00,\"Bananas\":1.00,\"Food\":1.00}"));
    }
}
