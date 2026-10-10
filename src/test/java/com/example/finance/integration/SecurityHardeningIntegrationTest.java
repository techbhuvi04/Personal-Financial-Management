package com.example.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.support.IntegrationTestBase;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(OutputCaptureExtension.class)
class SecurityHardeningIntegrationTest extends IntegrationTestBase {

    private static final String SECRET = "Sup3rSecretPassw0rd";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void passwordsNeverAppearInResponsesOrLogs(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        MvcResult registered = postJson(null, "/api/auth/register", Map.of("username", email, "password", SECRET,
                "fullName", "Secret Keeper", "phoneNumber", "+1234567890")).andExpect(status().isCreated()).andReturn();
        MvcResult duplicate = postJson(null, "/api/auth/register", Map.of("username", email, "password", SECRET,
                "fullName", "Secret Keeper", "phoneNumber", "+1234567890")).andExpect(status().isConflict()).andReturn();
        MvcResult badLogin = postJson(null, "/api/auth/login", Map.of("username", email, "password", SECRET + "x"))
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult invalid = postJson(null, "/api/auth/register", Map.of("username", "bad", "password", SECRET,
                "fullName", "X", "phoneNumber", "1")).andExpect(status().isBadRequest()).andReturn();

        for (MvcResult result : new MvcResult[]{registered, duplicate, badLogin, invalid}) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET).doesNotContain("$2a$");
        }
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test
    void forgedOrStaleSessionCookiesAreRejected() throws Exception {
        mockMvc.perform(get("/api/transactions").cookie(new Cookie("JSESSIONID", "FORGED0123456789ABCDEF")))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = newUserSession();
        session.invalidate();
        mockMvc.perform(get("/api/transactions").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void crossOriginRequestsGetNoCorsGrant() throws Exception {
        MockHttpSession session = newUserSession();

        mockMvc.perform(get("/api/categories").session(session).header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(options("/api/transactions").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void defaultCategoriesCannotBeModifiedThroughAnyEndpoint() throws Exception {
        MockHttpSession session = newUserSession();

        mockMvc.perform(put("/api/categories/Food").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Groceries\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isMethodNotAllowed());
        postJson(session, "/api/categories", Map.of("name", "  food  ", "type", "INCOME"))
                .andExpect(status().isConflict());
        getAs(session, "/api/categories")
                .andExpect(jsonPath("$.categories[1].name").value("Food"))
                .andExpect(jsonPath("$.categories[1].type").value("EXPENSE"));
    }

    @Test
    void duplicateCustomCategoryWithSurroundingSpacesIsAConflict() throws Exception {
        MockHttpSession session = newUserSession();
        createCategory(session, "Gifts", "EXPENSE");

        postJson(session, "/api/categories", Map.of("name", "  gifts  ", "type", "EXPENSE"))
                .andExpect(status().isConflict());
    }

    @Test
    void literalNullBodiesAndUnparseableIdsAreClientErrors() throws Exception {
        MockHttpSession session = newUserSession();

        mockMvc.perform(post("/api/transactions").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
        putJson(session, "/api/transactions/abc", Map.of("amount", 1)).andExpect(status().isBadRequest());
        getAs(session, "/api/goals/99999999999999999999").andExpect(status().isBadRequest());
        getAs(session, "/api/reports/monthly/2024/99999999999").andExpect(status().isBadRequest());
    }

    @Test
    void transactionListingUsesASingleQueryRegardlessOfSize() throws Exception {
        MockHttpSession session = newUserSession();
        createCategory(session, "Gifts", "EXPENSE");
        for (String category : new String[]{"Salary", "Food", "Rent", "Gifts", "Food", "Utilities"}) {
            createTransaction(session, "10", "2024-01-01", category);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        getAs(session, "/api/transactions").andExpect(jsonPath("$.transactions.length()").value(6));

        assertThat(statistics.getPrepareStatementCount()).as("SQL statements for the listing").isEqualTo(1);
        statistics.setStatisticsEnabled(false);
    }
}
