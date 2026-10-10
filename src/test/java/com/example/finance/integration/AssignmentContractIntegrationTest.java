package com.example.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Replays the request/response expectations of the assignment's grading script (financial_manager_tests.sh).
 * The script compares JSON values as text, so these tests assert exact serialized forms such as
 * {@code "progressPercentage":65.5} and {@code "netSavings":0}, not just numeric equality.
 */
class AssignmentContractIntegrationTest extends IntegrationTestBase {

    @Test
    void registrationAcceptsThePhoneNumbersUsedByTheGradingScript() throws Exception {
        for (String phone : new String[]{"+1234567890", "+0987654321", "+1111111111"}) {
            postJson(null, "/api/auth/register", Map.of("username", uniqueEmail(), "password", "anotherPassword123",
                    "fullName", "Jane Doe", "phoneNumber", phone))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void transactionUpdateIgnoresTheDateField() throws Exception {
        MockHttpSession session = newUserSession();
        long salary = createTransaction(session, "5000.00", "2024-01-15", "Salary");
        long food = createTransaction(session, "400.00", "2024-01-17", "Food");

        putJson(session, "/api/transactions/" + salary, Map.of("date", "2024-01-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2024-01-15"));
        putJson(session, "/api/transactions/" + food, Map.of("amount", new BigDecimal("450.00"),
                "date", "2024-01-18", "description", "Trying to change date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2024-01-17"))
                .andExpect(jsonPath("$.description").value("Trying to change date"))
                .andExpect(content().string(containsString("\"amount\":450.00")));

        assertThat(jdbcTemplate.queryForList("SELECT CAST(transaction_date AS VARCHAR) FROM transactions ORDER BY id",
                String.class)).containsExactly("2024-01-15", "2024-01-17");
    }

    @Test
    void categoryResponsesExposeTheCustomFlagUnderBothDocumentedNames() throws Exception {
        MockHttpSession session = newUserSession();

        postJson(session, "/api/categories", Map.of("name", "Freelance_1", "type", "INCOME"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"custom\":true")))
                .andExpect(jsonPath("$.isCustom").value(true));
        getAs(session, "/api/categories")
                .andExpect(jsonPath("$.categories[0].custom").value(false))
                .andExpect(jsonPath("$.categories[0].isCustom").value(false))
                .andExpect(jsonPath("$.categories[7].custom").value(true));
    }

    /**
     * Mirrors scenarios 2, 4, 5 and 6 of the grading script with the same data and the same textual expectations.
     */
    @Test
    void goalAndReportFiguresMatchTheGradingScriptExactly() throws Exception {
        MockHttpSession session = newUserSession();
        long salary = createTransaction(session, "5000.00", "2024-01-15", "Salary");
        long rent = createTransaction(session, "1200.00", "2024-01-16", "Rent");
        long food = createTransaction(session, "400.00", "2024-01-17", "Food");
        putJson(session, "/api/transactions/" + salary, Map.of("date", "2024-01-20")).andExpect(status().isOk());
        putJson(session, "/api/transactions/" + salary, Map.of("amount", 5500.00, "description", "bonus"))
                .andExpect(status().isOk());
        putJson(session, "/api/transactions/" + food, Map.of("amount", 450.00, "date", "2024-01-18"))
                .andExpect(status().isOk());
        deleteAs(session, "/api/transactions/" + rent).andExpect(status().isOk());
        createCategory(session, "Freelance_1", "INCOME");
        createTransaction(session, "1500.00", "2024-01-18", "Freelance_1");

        postJson(session, "/api/goals", Map.of("goalName", "Emergency Fund No Start Date", "targetAmount", 10000.00,
                "targetDate", "2027-01-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-10-10"));

        long emergency = goalId(postJson(session, "/api/goals", Map.of("goalName", "Emergency Fund",
                "targetAmount", 10000.00, "targetDate", "2027-01-01", "startDate", "2024-01-01"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"currentProgress\":6550.00,")))
                .andExpect(content().string(containsString("\"progressPercentage\":65.5,")))
                .andExpect(content().string(containsString("\"remainingAmount\":3450.00}")))
                .andReturn());

        long vacation = goalId(postJson(session, "/api/goals", Map.of("goalName", "Vacation Fund",
                "targetAmount", 5000.00, "targetDate", "2027-12-01", "startDate", "2024-02-01"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"currentProgress\":0,")))
                .andExpect(content().string(containsString("\"progressPercentage\":0.0,")))
                .andReturn());

        long febIncome = createTransaction(session, "3000.00", "2024-02-15", "Salary");
        long febExpense = createTransaction(session, "500.00", "2024-02-20", "Food");
        getAs(session, "/api/goals/" + vacation)
                .andExpect(content().string(containsString("\"currentProgress\":2500.00,")))
                .andExpect(content().string(containsString("\"progressPercentage\":50.0,")))
                .andExpect(content().string(containsString("\"remainingAmount\":2500.00}")));

        putJson(session, "/api/goals/" + emergency, Map.of("targetAmount", 15000.00))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"progressPercentage\":60.33,")))
                .andExpect(content().string(containsString("\"remainingAmount\":5950.00}")));
        putJson(session, "/api/goals/" + emergency, Map.of("targetDate", "2028-01-01")).andExpect(status().isOk());

        getAs(session, "/api/reports/monthly/2024/2")
                .andExpect(content().string(containsString("\"netSavings\":2500.00}")));
        deleteAs(session, "/api/transactions/" + febExpense).andExpect(status().isOk());
        getAs(session, "/api/goals/" + vacation)
                .andExpect(content().string(containsString("\"currentProgress\":3000.00,")))
                .andExpect(content().string(containsString("\"progressPercentage\":60.0,")));
        getAs(session, "/api/reports/monthly/2024/2")
                .andExpect(content().string(containsString("\"netSavings\":3000.00}")));
        deleteAs(session, "/api/transactions/" + febIncome).andExpect(status().isOk());
        getAs(session, "/api/goals/" + vacation)
                .andExpect(content().string(containsString("\"currentProgress\":0,")))
                .andExpect(content().string(containsString("\"progressPercentage\":0.0,")));
        getAs(session, "/api/reports/monthly/2024/2")
                .andExpect(content().string(containsString("\"netSavings\":0}")));

        getAs(session, "/api/reports/monthly/2024/1")
                .andExpect(content().string(containsString("\"netSavings\":6550.00}")))
                .andExpect(content().string(containsString("\"Freelance_1\":1500.00")));
        getAs(session, "/api/reports/monthly/2024/12")
                .andExpect(content().string(containsString("\"netSavings\":0}")));
        getAs(session, "/api/reports/yearly/2024")
                .andExpect(content().string(containsString("\"netSavings\":6550.00}")));
        getAs(session, "/api/reports/yearly/2023")
                .andExpect(content().string(containsString("\"netSavings\":0}")));
    }

    private long goalId(MvcResult result) throws Exception {
        return body(result).get("id").asLong();
    }
}
