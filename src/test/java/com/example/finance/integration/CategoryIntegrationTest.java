package com.example.finance.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpSession;

class CategoryIntegrationTest extends IntegrationTestBase {

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = newUserSession();
    }

    @Test
    void listReturnsSevenDefaultCategoriesInContractShape() throws Exception {
        getAs(session, "/api/categories")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(7)))
                .andExpect(jsonPath("$.categories[0].name").value("Salary"))
                .andExpect(jsonPath("$.categories[0].type").value("INCOME"))
                .andExpect(jsonPath("$.categories[0].isCustom").value(false))
                .andExpect(jsonPath("$.categories[*].name").value(org.hamcrest.Matchers.contains("Salary", "Food",
                        "Rent", "Transportation", "Entertainment", "Healthcare", "Utilities")));
    }

    @Test
    void createReturns201AndListShowsOwnCustomCategoriesOnly() throws Exception {
        postJson(session, "/api/categories", Map.of("name", " SideBusinessIncome ", "type", "INCOME"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("SideBusinessIncome"))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.isCustom").value(true));
        MockHttpSession other = newUserSession();
        createCategory(other, "OtherUsersCategory", "EXPENSE");

        getAs(session, "/api/categories")
                .andExpect(jsonPath("$.categories", hasSize(8)))
                .andExpect(jsonPath("$.categories[7].name").value("SideBusinessIncome"))
                .andExpect(jsonPath("$.categories[*].name").value(not(hasItem("OtherUsersCategory"))));
    }

    @Test
    void duplicateCustomNameIsCaseInsensitiveConflictPerUser() throws Exception {
        createCategory(session, "Freelance", "INCOME");

        postJson(session, "/api/categories", Map.of("name", "FREELANCE", "type", "EXPENSE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category 'FREELANCE' already exists"));
        postJson(newUserSession(), "/api/categories", Map.of("name", "Freelance", "type", "INCOME"))
                .andExpect(status().isCreated());
    }

    @Test
    void customCategoryCannotShadowDefault() throws Exception {
        postJson(session, "/api/categories", Map.of("name", "salary", "type", "INCOME"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("default category")));
    }

    @ParameterizedTest(name = "name=''{0}'' type={1}")
    @CsvSource(value = {
            "'',INCOME", "'   ',INCOME", "Bad/Name,INCOME", "-dash,INCOME", "Valid,SAVINGS", "Valid,NULL",
            "NULL,INCOME"}, nullValues = "NULL")
    void createWithInvalidInputReturns400(String name, String type) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("type", type);

        postJson(session, "/api/categories", request).andExpect(status().isBadRequest());
    }

    @Test
    void createWithTooLongNameReturns400() throws Exception {
        postJson(session, "/api/categories", Map.of("name", "a".repeat(51), "type", "INCOME"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Category name must be at most 50 characters"));
    }

    @Test
    void deleteOwnUnreferencedCategoryCaseInsensitively() throws Exception {
        createCategory(session, "Gifts", "EXPENSE");

        deleteAs(session, "/api/categories/gifts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Category deleted successfully"));
        getAs(session, "/api/categories").andExpect(jsonPath("$.categories", hasSize(7)));
        postJson(session, "/api/transactions", Map.of("amount", 1, "date", "2024-01-01", "category", "Gifts"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteDefaultCategoryIsForbidden() throws Exception {
        deleteAs(session, "/api/categories/Salary")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Default categories cannot be deleted"));
    }

    @Test
    void deleteReferencedCategoryIsRejectedAndTransactionsKept() throws Exception {
        createCategory(session, "Gifts", "EXPENSE");
        createTransaction(session, "25", "2024-01-01", "Gifts");

        deleteAs(session, "/api/categories/Gifts")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("is used by existing transactions")));
        getAs(session, "/api/transactions").andExpect(jsonPath("$.transactions", hasSize(1)));
    }

    @Test
    void deleteMissingOrAnotherUsersCategoryIsNotFound() throws Exception {
        MockHttpSession other = newUserSession();
        createCategory(other, "Private", "EXPENSE");

        deleteAs(session, "/api/categories/Private").andExpect(status().isNotFound());
        deleteAs(session, "/api/categories/Nothing").andExpect(status().isNotFound());
        getAs(other, "/api/categories").andExpect(jsonPath("$.categories", hasSize(8)));
    }

    @Test
    void deleteWithBlankNameIsRejected() throws Exception {
        deleteAs(session, "/api/categories/%20").andExpect(status().isBadRequest());
    }
}
