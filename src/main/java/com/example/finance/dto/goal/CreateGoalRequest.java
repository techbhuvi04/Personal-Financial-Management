package com.example.finance.dto.goal;

import com.example.finance.util.InputNormalizer;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for creating a savings goal.
 *
 * @param goalName     descriptive name, at most 100 characters
 * @param targetAmount positive amount with at most two decimal places
 * @param targetDate   must be after today
 * @param startDate    optional; defaults to today and may not be in the future
 */
public record CreateGoalRequest(
        @NotBlank(message = "Goal name is required")
        @Size(max = 100, message = "Goal name must be at most 100 characters")
        String goalName,

        @NotNull(message = "Target amount is required")
        @Positive(message = "Target amount must be greater than zero")
        @Digits(integer = 13, fraction = 2,
                message = "Target amount must have at most 13 integer digits and 2 decimal places")
        BigDecimal targetAmount,

        @NotNull(message = "Target date is required")
        LocalDate targetDate,

        LocalDate startDate) {

    public CreateGoalRequest {
        goalName = InputNormalizer.trim(goalName);
    }
}
