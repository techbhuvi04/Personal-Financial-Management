package com.example.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finance.support.DatabaseCleaner;
import com.example.finance.support.TestClockConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Fires genuinely concurrent HTTP requests at a real server. Races must surface as client errors (404/409/400),
 * never as 5xx, and must never break referential integrity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ConcurrentModificationIntegrationTest {

    private static final int ROUNDS = 25;
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private String cookie;

    @BeforeEach
    void loginFreshUser() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        send("POST", "/api/auth/register", "{\"username\":\"" + email + "\",\"password\":\"password123\","
                + "\"fullName\":\"Racer\",\"phoneNumber\":\"+1234567890\"}");
        HttpResponse<String> login = send("POST", "/api/auth/login",
                "{\"username\":\"" + email + "\",\"password\":\"password123\"}");
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        cookie = setCookie.substring(0, setCookie.indexOf(';'));
    }

    @AfterEach
    void clean() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void concurrentUpdateAndDeleteOfATransactionNeverFailWith5xx() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            String id = id(send("POST", "/api/transactions",
                    "{\"amount\":5,\"date\":\"2024-01-01\",\"category\":\"Food\"}"));
            statuses.addAll(race(
                    () -> send("PUT", "/api/transactions/" + id, "{\"amount\":6}"),
                    () -> send("DELETE", "/api/transactions/" + id, null)));
        }

        assertThat(statuses).allSatisfy(status -> assertThat(status).isIn(200, 404, 409));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
    }

    @Test
    void concurrentUpdateAndDeleteOfAGoalNeverFailWith5xx() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            String id = id(send("POST", "/api/goals",
                    "{\"goalName\":\"g\",\"targetAmount\":5,\"targetDate\":\"2027-01-01\"}"));
            statuses.addAll(race(
                    () -> send("PUT", "/api/goals/" + id, "{\"targetAmount\":7}"),
                    () -> send("DELETE", "/api/goals/" + id, null)));
        }

        assertThat(statuses).allSatisfy(status -> assertThat(status).isIn(200, 404, 409));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM savings_goals", Integer.class)).isZero();
    }

    @Test
    void concurrentDoubleDeleteNeverFailsWith5xx() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            String id = id(send("POST", "/api/transactions",
                    "{\"amount\":5,\"date\":\"2024-01-01\",\"category\":\"Food\"}"));
            statuses.addAll(race(
                    () -> send("DELETE", "/api/transactions/" + id, null),
                    () -> send("DELETE", "/api/transactions/" + id, null)));
        }

        assertThat(statuses).allSatisfy(status -> assertThat(status).isIn(200, 404, 409));
        assertThat(statuses).contains(200);
    }

    @Test
    void categoryDeletionRacingTransactionCreationNeverOrphansATransaction() throws Exception {
        for (int i = 0; i < ROUNDS; i++) {
            String name = "Race" + i;
            send("POST", "/api/categories", "{\"name\":\"" + name + "\",\"type\":\"EXPENSE\"}");
            List<Integer> statuses = race(
                    () -> send("DELETE", "/api/categories/" + name, null),
                    () -> send("POST", "/api/transactions",
                            "{\"amount\":5,\"date\":\"2024-01-01\",\"category\":\"" + name + "\"}"));
            int deleteStatus = statuses.get(0);
            int createStatus = statuses.get(1);

            assertThat(deleteStatus).isIn(200, 400, 409);
            assertThat(createStatus).isIn(201, 400, 409);
            assertThat(deleteStatus == 200 && createStatus == 201)
                    .as("category deleted while a transaction referencing it was created").isFalse();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions t "
                + "LEFT JOIN categories c ON c.id = t.category_id WHERE c.id IS NULL", Integer.class)).isZero();
    }

    private List<Integer> race(ThrowingCall first, ThrowingCall second) {
        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> first.statusOrFail());
        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> second.statusOrFail());
        return List.of(a.join(), b.join());
    }

    private static String id(HttpResponse<String> response) {
        Matcher matcher = ID.matcher(response.body());
        assertThat(matcher.find()).as("id in %s", response.body()).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (json != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @FunctionalInterface
    private interface ThrowingCall {
        HttpResponse<String> call() throws Exception;

        default int statusOrFail() {
            try {
                return call().statusCode();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
