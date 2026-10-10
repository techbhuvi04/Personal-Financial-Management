package com.example.finance.dto.auth;

import com.example.finance.dto.validation.MaxUtf8Bytes;
import com.example.finance.util.InputNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Username is trimmed and lower-cased, full name trimmed and phone separators removed on
 * construction, before validation runs.
 *
 * @param username    email address used as login name
 * @param password    8-72 characters (at most 72 UTF-8 bytes) with at least one letter and one digit
 * @param fullName    the user's full name
 * @param phoneNumber 7-15 digits with an optional leading '+'
 */
public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 254, message = "Username must be at most 254 characters")
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "Username must be a valid email address")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @MaxUtf8Bytes(value = 72, message = "Password must be at most 72 bytes when UTF-8 encoded")
        @Pattern(regexp = ValidationPatterns.PASSWORD,
                message = "Password must contain at least one letter and one digit and no whitespace")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        @Pattern(regexp = ValidationPatterns.FULL_NAME,
                message = "Full name may only contain letters, spaces, apostrophes, periods and hyphens")
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = ValidationPatterns.PHONE,
                message = "Phone number must contain 7 to 15 digits with an optional leading '+'")
        String phoneNumber) {

    public RegisterRequest {
        username = InputNormalizer.email(username);
        fullName = InputNormalizer.trim(fullName);
        phoneNumber = InputNormalizer.phone(phoneNumber);
    }

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=****, fullName=" + fullName + "]";
    }
}
