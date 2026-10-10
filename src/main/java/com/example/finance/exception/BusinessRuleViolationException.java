package com.example.finance.exception;

/**
 * The request is well-formed but violates a business rule (e.g. a future transaction date). Mapped to HTTP 400.
 */
public class BusinessRuleViolationException extends RuntimeException {

    /**
     * @param message client-safe description
     */
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
