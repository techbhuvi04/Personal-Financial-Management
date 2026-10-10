package com.example.finance.dto.goal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial update of a savings goal. Only the target amount and target date may change; absent fields are left
 * unchanged and any other field in the request body (goal name, start date) is ignored.
 *
 * @param targetAmount new positive target
 * @param targetDate   new target date, must be after today
 */
public record UpdateGoalRequest(
        @Positive(message = "Target amount must be greater than zero")
        @Digits(integer = 13, fraction = 2,
                message = "Target amount must have at most 13 integer digits and 2 decimal places")
        BigDecimal targetAmount,

        LocalDate targetDate) {
}
