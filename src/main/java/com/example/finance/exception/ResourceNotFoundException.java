package com.example.finance.exception;

/**
 * The requested resource does not exist or is not visible to the current user. Mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @param message client-safe description
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
