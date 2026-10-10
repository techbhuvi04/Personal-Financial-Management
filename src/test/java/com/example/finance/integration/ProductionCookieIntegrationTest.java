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
 * With the production default ({@code COOKIE_SECURE=true}) the session cookie carries the Secure attribute.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=true")
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ProductionCookieIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clean() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void sessionCookieIsSecureHttpOnlyAndSameSiteStrict() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String email = "secure-" + UUID.randomUUID() + "@example.com";
        client.send(json("/api/auth/register", "{\"username\":\"" + email + "\",\"password\":\"password123\","
                + "\"fullName\":\"Secure\",\"phoneNumber\":\"+1234567890\"}"), HttpResponse.BodyHandlers.discarding());

        HttpResponse<String> login = client.send(json("/api/auth/login",
                "{\"username\":\"" + email + "\",\"password\":\"password123\"}"), HttpResponse.BodyHandlers.ofString());

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Set-Cookie").orElseThrow())
                .contains("Secure").contains("HttpOnly").contains("SameSite=Strict");
    }

    private HttpRequest json(String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
