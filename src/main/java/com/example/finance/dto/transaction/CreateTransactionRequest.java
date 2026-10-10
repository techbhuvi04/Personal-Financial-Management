package com.example.finance.dto.transaction;

import com.example.finance.entity.enums.TransactionType;
import com.example.finance.util.InputNormalizer;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for creating a transaction. The type is derived from the category; if a type is supplied it must
 * match the category's type.
 *
 * @param amount      positive amount with at most two decimal places
 * @param date        transaction date (yyyy-MM-dd), not in the future
 * @param category    name of a default or own custom category (case-insensitive)
 * @param description optional text, at most 255 characters
 * @param type        optional; must equal the category type when present
 */
public record CreateTransactionRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Amount must have at most 13 integer digits and 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Date is required")
        LocalDate date,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must be at most 50 characters")
        String category,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        TransactionType type) {

    public CreateTransactionRequest {
        category = InputNormalizer.trim(category);
        description = InputNormalizer.trimToNull(description);
    }
}
