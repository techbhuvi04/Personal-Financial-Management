package com.example.finance.dto.auth;

/**
 * Registration result.
 *
 * @param message confirmation text
 * @param userId  id of the new account
 */
public record RegisterResponse(String message, Long userId) {
}
