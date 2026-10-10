package com.example.finance.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;

class ReportIntegrationTest extends IntegrationTestBase {

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = newUserSession();
        createCategory(session, "Freelance", "INCOME");
    }

    @Test
    void monthlyReportGroupsByCategoryWithinMonthBoundaries() throws Exception {
        createTransaction(session, "999", "2023-12-31", "Salary");
        createTransaction(session, "3000", "2024-01-01", "Salary");
        createTransaction(session, "500", "2024-01-31", "Freelance");
        createTransaction(session, "150", "2024-01-05", "Food");
        createTransaction(session, "250", "2024-01-20", "Food");
        createTransaction(session, "1200", "2024-01-10", "Rent");
        createTransaction(session, "200", "2024-01-11", "Transportation");
        createTransaction(session, "888", "2024-02-01", "Rent");

        getAs(session, "/api/reports/monthly/2024/1")
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"month":1,"year":2024,
                         "totalIncome":{"Freelance":500.00,"Salary":3000.00},
                         "totalExpenses":{"Food":400.00,"Rent":1200.00,"Transportation":200.00},
                         "netSavings":1700.00}""", true))
                .andExpect(content().string(containsString("\"netSavings\":1700.00")));
    }

    @Test
    void monthlyReportHandlesLeapDayAndEmptyMonths() throws Exception {
        createTransaction(session, "29", "2024-02-29", "Food");

        getAs(session, "/api/reports/monthly/2024/2")
                .andExpect(jsonPath("$.totalExpenses.Food").value(29.0))
                .andExpect(jsonPath("$.netSavings").value(-29.0));
        getAs(session, "/api/reports/monthly/2024/3")
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"month":3,"year":2024,"totalIncome":{},"totalExpenses":{},"netSavings":0.00}""", true))
                .andExpect(content().string(containsString("\"netSavings\":0.00")));
    }

    @Test
    void yearlyReportAggregatesWholeCalendarYear() throws Exception {
        createTransaction(session, "1", "2023-12-31", "Salary");
        createTransaction(session, "3000", "2024-01-01", "Salary");
        createTransaction(session, "3000", "2024-12-31", "Salary");
        createTransaction(session, "600", "2024-06-15", "Freelance");
        createTransaction(session, "1200", "2024-03-01", "Rent");
        createTransaction(session, "1200", "2024-04-01", "Rent");
        createTransaction(session, "7", "2025-01-01", "Rent");

        getAs(session, "/api/reports/yearly/2024")
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"year":2024,
                         "totalIncome":{"Freelance":600.00,"Salary":6000.00},
                         "totalExpenses":{"Rent":2400.00},
                         "netSavings":4200.00}""", true));
    }

    @Test
    void reportsIncludeOnlyOwnTransactionsAndReflectDeletionsAndUpdates() throws Exception {
        long rent = createTransaction(session, "1000", "2024-05-01", "Rent");
        createTransaction(session, "2000", "2024-05-02", "Salary");
        MockHttpSession other = newUserSession();
        createTransaction(other, "777", "2024-05-03", "Salary");

        getAs(session, "/api/reports/monthly/2024/5").andExpect(jsonPath("$.netSavings").value(1000.0));
        putJson(session, "/api/transactions/" + rent, java.util.Map.of("amount", 1500)).andExpect(status().isOk());
        getAs(session, "/api/reports/monthly/2024/5").andExpect(jsonPath("$.netSavings").value(500.0));
        deleteAs(session, "/api/transactions/" + rent).andExpect(status().isOk());
        getAs(session, "/api/reports/yearly/2024")
                .andExpect(jsonPath("$.totalExpenses").isEmpty())
                .andExpect(jsonPath("$.netSavings").value(2000.0));
        getAs(other, "/api/reports/yearly/2024").andExpect(jsonPath("$.netSavings").value(777.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reports/monthly/2024/0", "/api/reports/monthly/2024/13",
            "/api/reports/monthly/2024/-1", "/api/reports/monthly/1899/5", "/api/reports/yearly/10000",
            "/api/reports/yearly/0", "/api/reports/monthly/2024/jan", "/api/reports/yearly/abcd"})
    void invalidPeriodsReturn400(String path) throws Exception {
        getAs(session, path)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
