package com.example.finance.dto.auth;

import com.example.finance.util.InputNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. The username is normalized the same way as at registration.
 *
 * @param username registered email address
 * @param password plaintext password
 */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 254, message = "Username must be at most 254 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password) {

    public LoginRequest {
        username = InputNormalizer.email(username);
    }

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}
