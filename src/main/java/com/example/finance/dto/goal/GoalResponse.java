package com.example.finance.dto.goal;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Savings goal with derived progress values.
 *
 * @param id                 goal id
 * @param goalName           name
 * @param targetAmount       target
 * @param targetDate         target date
 * @param startDate          first date counted towards progress
 * @param currentProgress    net income minus expenses from startDate through today
 * @param progressPercentage currentProgress / targetAmount * 100, rounded HALF_UP to two decimals and
 *                           serialized as a plain JSON number (e.g. 65.5, 0.0, 16.67), not capped
 * @param remainingAmount    targetAmount - currentProgress, not capped
 */
public record GoalResponse(Long id, String goalName, BigDecimal targetAmount, LocalDate targetDate,
                           LocalDate startDate, BigDecimal currentProgress, double progressPercentage,
                           BigDecimal remainingAmount) {
}
