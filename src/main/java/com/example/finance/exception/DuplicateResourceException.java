package com.example.finance.exception;

/**
 * The request would create a resource that already exists. Mapped to HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

    /**
     * @param message client-safe description
     */
    public DuplicateResourceException(String message) {
        super(message);
    }
}
