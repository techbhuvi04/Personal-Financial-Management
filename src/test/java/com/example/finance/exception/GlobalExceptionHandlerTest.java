package com.example.finance.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;

/**
 * Covers handler branches that are hard to trigger through HTTP without contrived endpoints.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");

    @Test
    void unexpectedExceptionBecomesGeneric500WithoutDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("secret internal detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred")
                .doesNotContain("secret");
    }

    @Test
    void dataIntegrityViolationBecomes409WithoutSql() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(new DataIntegrityViolationException(
                "could not execute statement", new SQLException("duplicate key value violates uk_users_username")),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).doesNotContain("uk_users_username");
        assertThat(response.getBody().path()).isEqualTo("/api/test");
    }

    @Test
    void constraintViolationBecomes400() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample(-1));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(
                new ConstraintViolationException(violations), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Value must be positive");
    }

    @Test
    void missingParameterBecomes400() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(
                new MissingServletRequestParameterException("year", "int"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Missing required parameter 'year'");
    }

    @Test
    void securityExceptionsReachingMvcMapTo401And403() {
        assertThat(handler.handleAuthentication(new BadCredentialsException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void databaseOutagesBecome503EvenDuringLogin() {
        assertThat(handler.handleDependencyFailure(
                new org.springframework.security.authentication.InternalAuthenticationServiceException("db down"),
                request).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.handleDependencyFailure(
                new org.springframework.transaction.CannotCreateTransactionException("db down"),
                request).getBody().message()).isEqualTo("Service temporarily unavailable, please retry later");
    }

    @Test
    void concurrentModificationBecomes409() {
        ResponseEntity<ErrorResponse> response = handler.handleConcurrentModification(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Object.class, 1L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("modified or deleted by another request");
    }

    @Test
    void errorResponseOmitsEmptyFieldErrors() {
        ErrorResponse response = ErrorResponse.of(HttpStatus.NOT_FOUND, "missing", "/x");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("Not Found");
        assertThat(response.fieldErrors()).isEmpty();
    }

    record Sample(@Positive(message = "Value must be positive") int value) {
    }
}
