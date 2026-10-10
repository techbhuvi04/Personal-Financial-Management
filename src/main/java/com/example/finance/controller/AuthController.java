package com.example.finance.controller;

import com.example.finance.dto.auth.CsrfTokenResponse;
import com.example.finance.dto.auth.LoginRequest;
import com.example.finance.dto.auth.RegisterRequest;
import com.example.finance.dto.auth.RegisterResponse;
import com.example.finance.dto.common.MessageResponse;
import com.example.finance.security.SessionAuthenticationHandler;
import com.example.finance.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, session login/logout and CSRF token bootstrap.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticationHandler sessionHandler;

    /**
     * @param authService    registration and credential checks
     * @param sessionHandler session establishment and teardown
     */
    public AuthController(AuthService authService, SessionAuthenticationHandler sessionHandler) {
        this.authService = authService;
        this.sessionHandler = sessionHandler;
    }

    /**
     * @param request registration data
     * @return the new user id
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return new RegisterResponse("User registered successfully", authService.register(request));
    }

    /**
     * Verifies credentials and binds the identity to a fresh session; the response sets the session cookie.
     *
     * @param request      credentials
     * @param httpRequest  current request
     * @param httpResponse current response
     * @return confirmation
     */
    @PostMapping("/login")
    public MessageResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        Authentication authentication = authService.authenticate(request);
        sessionHandler.establish(authentication, httpRequest, httpResponse);
        return new MessageResponse("Login successful");
    }

    /**
     * Invalidates the current session. Requires an authenticated session (401 otherwise).
     *
     * @param authentication current authentication
     * @param httpRequest    current request
     * @param httpResponse   current response
     * @return confirmation
     */
    @PostMapping("/logout")
    public MessageResponse logout(Authentication authentication, HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        sessionHandler.terminate(authentication, httpRequest, httpResponse);
        return new MessageResponse("Logout successful");
    }

    /**
     * Returns the CSRF token browser clients must echo when CSRF protection is enabled. Reading the token also
     * sets the {@code XSRF-TOKEN} cookie.
     *
     * @param csrfToken token resolved by Spring Security, or null when CSRF protection is disabled
     * @return token details
     */
    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        if (csrfToken == null) {
            return new CsrfTokenResponse(false, null, null);
        }
        return new CsrfTokenResponse(true, csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
