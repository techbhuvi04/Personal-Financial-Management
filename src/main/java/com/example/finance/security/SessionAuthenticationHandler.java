package com.example.finance.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Binds an authenticated identity to the HTTP session on login and tears it down on logout.
 */
@Component
public class SessionAuthenticationHandler {

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy sessionFixationProtection = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler cookieClearingHandler;

    /**
     * @param securityContextRepository repository that persists the security context in the session
     * @param sessionCookieName         name of the session cookie to clear on logout
     */
    public SessionAuthenticationHandler(SecurityContextRepository securityContextRepository,
                                        @Value("${server.servlet.session.cookie.name:JSESSIONID}")
                                        String sessionCookieName) {
        this.securityContextRepository = securityContextRepository;
        this.cookieClearingHandler = new CookieClearingLogoutHandler(sessionCookieName);
        this.logoutHandler.setSecurityContextRepository(securityContextRepository);
    }

    /**
     * Stores the authentication in a session whose id is changed if a session already existed, which prevents
     * session fixation.
     *
     * @param authentication successful authentication
     * @param request        current request
     * @param response       current response
     */
    public void establish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        sessionFixationProtection.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /**
     * Invalidates the session, clears the security context and expires the session cookie.
     *
     * @param authentication current authentication
     * @param request        current request
     * @param response       current response
     */
    public void terminate(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(request, response, authentication);
        cookieClearingHandler.logout(request, response, authentication);
    }
}
