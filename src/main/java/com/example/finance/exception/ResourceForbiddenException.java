package com.example.finance.exception;

/**
 * The resource exists but the current user may not access or modify it. Mapped to HTTP 403.
 */
public class ResourceForbiddenException extends RuntimeException {

    /**
     * @param message client-safe description
     */
    public ResourceForbiddenException(String message) {
        super(message);
    }
}
