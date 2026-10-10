package com.example.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finance.support.DatabaseCleaner;
import com.example.finance.support.TestClockConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the real servlet container to verify session cookie attributes and the cookie round trip that API
 * clients such as curl rely on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class SessionCookieIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void clean() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void sessionCookieIsHttpOnlySameSiteAndWorksUntilLogout() throws Exception {
        String email = "cookie-" + UUID.randomUUID() + "@example.com";
        String credentials = "{\"username\":\"" + email + "\",\"password\":\"password123\"}";
        HttpResponse<String> registered = send(post("/api/auth/register", "{\"username\":\"" + email
                + "\",\"password\":\"password123\",\"fullName\":\"Cookie Tester\",\"phoneNumber\":\"+1234567890\"}"));
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.headers().firstValue("Set-Cookie")).isEmpty();

        HttpResponse<String> login = send(post("/api/auth/login", credentials));
        assertThat(login.statusCode()).isEqualTo(200);
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).startsWith("JSESSIONID=").contains("HttpOnly").contains("SameSite=Strict");
        String sessionCookie = setCookie.substring(0, setCookie.indexOf(';'));

        HttpResponse<String> categories = send(HttpRequest.newBuilder(uri("/api/categories"))
                .header("Cookie", sessionCookie).GET().build());
        assertThat(categories.statusCode()).isEqualTo(200);
        assertThat(categories.body()).contains("\"categories\"");

        HttpResponse<String> logout = send(HttpRequest.newBuilder(uri("/api/auth/logout"))
                .header("Cookie", sessionCookie).POST(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(logout.statusCode()).isEqualTo(200);

        HttpResponse<String> afterLogout = send(HttpRequest.newBuilder(uri("/api/categories"))
                .header("Cookie", sessionCookie).GET().build());
        assertThat(afterLogout.statusCode()).isEqualTo(401);
        assertThat(afterLogout.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/json"));
    }

    @Test
    void failedLoginDoesNotIssueSessionCookie() throws Exception {
        HttpResponse<String> login = send(post("/api/auth/login",
                "{\"username\":\"nobody@example.com\",\"password\":\"password123\"}"));

        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(login.headers().firstValue("Set-Cookie")).isEmpty();
    }

    private HttpRequest post(String path, String json) {
        return HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
