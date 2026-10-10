package com.example.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.finance.dto.report.MonthlyReportResponse;
import com.example.finance.dto.report.YearlyReportResponse;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.exception.BusinessRuleViolationException;
import com.example.finance.repository.TransactionRepository;
import com.example.finance.repository.projection.CategoryTotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private ReportService service;

    @Test
    void monthlySplitsIncomeAndExpensesAndComputesNet() {
        when(transactionRepository.sumByCategory(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)))
                .thenReturn(List.of(
                        new CategoryTotal("Food", TransactionType.EXPENSE, new BigDecimal("400")),
                        new CategoryTotal("Freelance", TransactionType.INCOME, new BigDecimal("500")),
                        new CategoryTotal("Rent", TransactionType.EXPENSE, new BigDecimal("1200")),
                        new CategoryTotal("Salary", TransactionType.INCOME, new BigDecimal("3000"))));

        MonthlyReportResponse report = service.monthly(1L, 2024, 1);

        assertThat(report.month()).isEqualTo(1);
        assertThat(report.year()).isEqualTo(2024);
        assertThat(report.totalIncome()).containsExactly(
                org.assertj.core.api.Assertions.entry("Freelance", new BigDecimal("500.00")),
                org.assertj.core.api.Assertions.entry("Salary", new BigDecimal("3000.00")));
        assertThat(report.totalExpenses()).containsOnlyKeys("Food", "Rent");
        assertThat(report.netSavings()).hasToString("1900.00");
    }

    @Test
    void decemberRangeEndsAtFirstOfNextYearAndEmptyMonthIsZero() {
        when(transactionRepository.sumByCategory(1L, LocalDate.of(2024, 12, 1), LocalDate.of(2025, 1, 1)))
                .thenReturn(List.of());

        MonthlyReportResponse report = service.monthly(1L, 2024, 12);

        assertThat(report.totalIncome()).isEmpty();
        assertThat(report.totalExpenses()).isEmpty();
        assertThat(report.netSavings()).hasToString("0.00");
    }

    @Test
    void yearlyCoversWholeCalendarYear() {
        when(transactionRepository.sumByCategory(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)))
                .thenReturn(List.of(new CategoryTotal("Rent", TransactionType.EXPENSE, new BigDecimal("14400"))));

        YearlyReportResponse report = service.yearly(1L, 2024);

        assertThat(report.year()).isEqualTo(2024);
        assertThat(report.netSavings()).hasToString("-14400.00");
    }

    @ParameterizedTest
    @CsvSource({"2024, 0", "2024, 13", "1899, 1", "10000, 1"})
    void monthlyRejectsInvalidPeriods(int year, int month) {
        assertThatThrownBy(() -> service.monthly(1L, year, month)).isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(transactionRepository);
    }

    @ParameterizedTest
    @CsvSource({"1899", "10000", "-5"})
    void yearlyRejectsInvalidYears(int year) {
        assertThatThrownBy(() -> service.yearly(1L, year)).isInstanceOf(BusinessRuleViolationException.class);
    }
}
