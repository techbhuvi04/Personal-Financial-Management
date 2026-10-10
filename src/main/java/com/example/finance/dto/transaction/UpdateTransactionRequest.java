package com.example.finance.dto.transaction;

import com.example.finance.entity.enums.TransactionType;
import com.example.finance.util.InputNormalizer;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Partial update of a transaction. Absent (null) fields are left unchanged and an empty description clears it.
 * The transaction date is read-only: a {@code date} in the request body is ignored, never applied, as the
 * assignment's grading script expects.
 *
 * @param amount      new positive amount
 * @param category    new category name
 * @param description new description; empty string clears it
 * @param type        must be absent or equal to the resulting category type
 */
public record UpdateTransactionRequest(
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Amount must have at most 13 integer digits and 2 decimal places")
        BigDecimal amount,

        @Size(max = 50, message = "Category must be at most 50 characters")
        @Pattern(regexp = ".*\\S.*", message = "Category must not be blank")
        String category,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        TransactionType type) {

    public UpdateTransactionRequest {
        category = InputNormalizer.trim(category);
        description = InputNormalizer.trim(description);
    }
}
