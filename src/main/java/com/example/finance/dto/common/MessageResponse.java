package com.example.finance.dto.common;

/**
 * Simple confirmation body, e.g. {@code {"message": "Logout successful"}}.
 *
 * @param message human-readable outcome
 */
public record MessageResponse(String message) {
}
