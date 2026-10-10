package com.example.finance.config;

import com.example.finance.security.SecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Session-based security: registration, login, the CSRF bootstrap endpoint, health and API docs are public;
 * everything else requires an authenticated session. Errors are rendered as JSON by {@link SecurityErrorHandler}.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {"/api/auth/register", "/api/auth/login"};
    private static final String[] DOC_ENDPOINTS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
            "/swagger-ui/**"};

    /**
     * @param http               security builder
     * @param errorHandler       JSON 401/403 writer
     * @param contextRepository  session-backed security context storage
     * @param appProperties      CSRF toggle
     * @param serverProperties   cookie settings reused for the CSRF cookie
     * @return the filter chain
     * @throws Exception if the configuration is invalid
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errorHandler,
                                                   SecurityContextRepository contextRepository,
                                                   AppProperties appProperties,
                                                   ServerProperties serverProperties) throws Exception {
        http
                .csrf(csrf -> configureCsrf(csrf, appProperties, serverProperties))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(DOC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    private static void configureCsrf(CsrfConfigurer<HttpSecurity> csrf, AppProperties appProperties,
                                      ServerProperties serverProperties) {
        if (!appProperties.security().csrfEnabled()) {
            // Non-browser API clients (curl, the assignment test script) cannot echo a token. Cross-site request
            // forgery is still mitigated by the SameSite=Strict, HttpOnly session cookie, the absence of any CORS
            // configuration and JSON-only request bodies. See README "CSRF".
            csrf.disable();
            return;
        }
        Boolean secure = serverProperties.getServlet().getSession().getCookie().getSecure();
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookieCustomizer(cookie -> cookie.secure(Boolean.TRUE.equals(secure)).sameSite("Strict"));
        csrf.csrfTokenRepository(tokenRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(PUBLIC_AUTH_ENDPOINTS);
    }

    /**
     * @return repository that keeps the security context in the HTTP session (and request for the current call)
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    /**
     * @return BCrypt encoder with the default strength (10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * @param userDetailsService user lookup
     * @param passwordEncoder    password verification
     * @return manager used by the login endpoint
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
