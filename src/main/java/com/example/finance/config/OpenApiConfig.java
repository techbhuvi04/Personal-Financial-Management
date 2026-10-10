package com.example.finance.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata served at {@code /v3/api-docs} and {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    private static final String SESSION_SCHEME = "sessionCookie";

    /**
     * @param cookieName session cookie name
     * @return API description declaring session-cookie authentication
     */
    @Bean
    public OpenAPI financeOpenApi(@Value("${server.servlet.session.cookie.name:JSESSIONID}") String cookieName) {
        return new OpenAPI()
                .info(new Info()
                        .title("Personal Finance Manager API")
                        .version("1.0.0")
                        .description("Track income, expenses, savings goals and reports. Authenticate with "
                                + "POST /api/auth/login and send the returned session cookie on later requests."))
                .components(new Components().addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(cookieName)))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }
}
