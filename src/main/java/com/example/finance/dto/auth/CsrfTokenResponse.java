package com.example.finance.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CSRF token details for browser clients when CSRF protection is enabled.
 *
 * @param csrfEnabled whether state-changing requests must carry the token
 * @param headerName  header to send the token in, or null when disabled
 * @param token       token value, or null when disabled
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsrfTokenResponse(boolean csrfEnabled, String headerName, String token) {
}
