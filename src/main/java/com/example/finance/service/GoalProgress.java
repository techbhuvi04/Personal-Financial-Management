package com.example.finance.service;

import com.example.finance.util.MonetaryAmounts;
import java.math.BigDecimal;

/**
 * Derived progress of a savings goal. Values are not capped: progress may be negative (expenses exceeded income)
 * or exceed the target, in which case the percentage is above 100 and the remaining amount is negative.
 *
 * @param currentProgress    net savings counted towards the goal
 * @param progressPercentage currentProgress / target * 100, two decimals, HALF_UP
 * @param remainingAmount    target - currentProgress
 */
public record GoalProgress(BigDecimal currentProgress, BigDecimal progressPercentage, BigDecimal remainingAmount) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param currentProgress net savings since the goal start date
     * @param targetAmount    positive goal target
     * @return progress values at the monetary scale
     */
    public static GoalProgress of(BigDecimal currentProgress, BigDecimal targetAmount) {
        BigDecimal progress = MonetaryAmounts.normalize(currentProgress);
        BigDecimal target = MonetaryAmounts.normalize(targetAmount);
        BigDecimal percentage = progress.multiply(HUNDRED)
                .divide(target, MonetaryAmounts.SCALE, MonetaryAmounts.ROUNDING);
        return new GoalProgress(progress, percentage, target.subtract(progress));
    }
}
