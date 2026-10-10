package com.example.finance.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Uniform error body for every 4xx/5xx response, produced both by MVC exception handling and by the security
 * filter chain.
 *
 * @param status      HTTP status code
 * @param error       HTTP reason phrase
 * @param message     human-readable description, safe to show to clients
 * @param path        request path
 * @param timestamp   time the error was produced
 * @param fieldErrors per-field validation messages; omitted when empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(int status, String error, String message, String path, Instant timestamp,
                            Map<String, String> fieldErrors) {

    /**
     * @param status  HTTP status
     * @param message client-safe description
     * @param path    request path
     * @return an error body without field errors
     */
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, Map.of());
    }

    /**
     * @param status      HTTP status
     * @param message     client-safe description
     * @param path        request path
     * @param fieldErrors per-field messages
     * @return an error body
     */
    public static ErrorResponse of(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, Instant.now(), fieldErrors);
    }
}
