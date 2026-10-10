package com.example.finance.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits a string's length in UTF-8 bytes. Used for passwords because BCrypt only considers the first 72 bytes:
 * a character-count limit alone would let multi-byte passwords be silently truncated.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    /** @return maximum number of UTF-8 bytes */
    int value();

    /** @return violation message */
    String message() default "must be at most {value} bytes when UTF-8 encoded";

    /** @return validation groups */
    Class<?>[] groups() default {};

    /** @return payload */
    Class<? extends Payload>[] payload() default {};
}
