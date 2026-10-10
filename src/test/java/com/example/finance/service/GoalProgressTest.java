package com.example.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GoalProgressTest {

    @ParameterizedTest(name = "progress={0} target={1} -> {2}% remaining {3}")
    @CsvSource({
            "0, 5000, 0.00, 5000.00",
            "1000, 5000, 20.00, 4000.00",
            "1000, 6000, 16.67, 5000.00",
            "5000, 5000, 100.00, 0",
            "7500, 5000, 150.00, -2500.00",
            "-250, 1000, -25.00, 1250.00",
            "1, 3, 33.33, 2.00",
            "2, 3, 66.67, 1.00",
            "0.005, 1, 1.00, 0.99"})
    void computesPercentageAndRemainingWithHalfUpRounding(String progress, String target, String percentage,
                                                         String remaining) {
        GoalProgress result = GoalProgress.of(new BigDecimal(progress), new BigDecimal(target));

        assertThat(result.progressPercentage()).hasToString(percentage);
        assertThat(result.remainingAmount()).hasToString(remaining);
        assertThat(result.currentProgress().signum() == 0 ? 0 : result.currentProgress().scale())
                .as("non-zero amounts carry two decimals").isIn(0, 2);
    }
}
