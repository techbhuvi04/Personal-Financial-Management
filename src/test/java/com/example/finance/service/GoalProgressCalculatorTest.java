package com.example.finance.service;

import static com.example.finance.service.TestEntities.goal;
import static com.example.finance.service.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.finance.entity.enums.TransactionType;
import com.example.finance.repository.TransactionRepository;
import com.example.finance.repository.projection.TypeTotal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalProgressCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Mock
    private TransactionRepository transactionRepository;

    private GoalProgressCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new GoalProgressCalculator(transactionRepository,
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void netsIncomeAgainstExpensesFromStartDateThroughToday() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        when(transactionRepository.sumByType(1L, start, TODAY)).thenReturn(List.of(
                new TypeTotal(TransactionType.INCOME, new BigDecimal("3000")),
                new TypeTotal(TransactionType.EXPENSE, new BigDecimal("2000"))));

        GoalProgress progress = calculator.calculate(goal(1, user(1), "5000", TODAY.plusYears(1), start));

        assertThat(progress.currentProgress()).hasToString("1000.00");
        assertThat(progress.progressPercentage()).hasToString("20.00");
        assertThat(progress.remainingAmount()).hasToString("4000.00");
    }

    @Test
    void noTransactionsMeansZeroProgress() {
        when(transactionRepository.sumByType(1L, TODAY, TODAY)).thenReturn(List.of());

        GoalProgress progress = calculator.calculate(goal(1, user(1), "100", TODAY.plusDays(1), TODAY));

        assertThat(progress.currentProgress()).hasToString("0");
        assertThat(progress.progressPercentage()).hasToString("0.00");
        assertThat(progress.remainingAmount()).hasToString("100.00");
    }

    @Test
    void startDateAfterTodayCountsNothing() {
        GoalProgress progress = calculator.calculate(goal(1, user(1), "100", TODAY.plusYears(1), TODAY.plusDays(1)));

        assertThat(progress.currentProgress()).hasToString("0");
        verifyNoInteractions(transactionRepository);
    }
}
