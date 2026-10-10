package com.example.finance.service;

import com.example.finance.dto.report.MonthlyReportResponse;
import com.example.finance.dto.report.YearlyReportResponse;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.exception.BusinessRuleViolationException;
import com.example.finance.repository.TransactionRepository;
import com.example.finance.repository.projection.CategoryTotal;
import com.example.finance.util.MonetaryAmounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly and yearly income/expense reports, aggregated in the database by category over transaction dates.
 */
@Service
public class ReportService {

    static final int MIN_YEAR = 1900;
    static final int MAX_YEAR = 9999;

    private final TransactionRepository transactionRepository;

    /**
     * @param transactionRepository aggregation queries
     */
    public ReportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * @param userId current user
     * @param year   calendar year ({@value #MIN_YEAR}-{@value #MAX_YEAR})
     * @param month  1-12
     * @return totals for {@code [first day of month, first day of next month)}
     */
    @Transactional(readOnly = true)
    public MonthlyReportResponse monthly(Long userId, int year, int month) {
        validateYear(year);
        if (month < 1 || month > 12) {
            throw new BusinessRuleViolationException("Month must be between 1 and 12");
        }
        LocalDate from = LocalDate.of(year, month, 1);
        Totals totals = aggregate(userId, from, from.plusMonths(1));
        return new MonthlyReportResponse(month, year, totals.income(), totals.expenses(), totals.netSavings());
    }

    /**
     * @param userId current user
     * @param year   calendar year ({@value #MIN_YEAR}-{@value #MAX_YEAR})
     * @return totals for {@code [January 1, January 1 of next year)}
     */
    @Transactional(readOnly = true)
    public YearlyReportResponse yearly(Long userId, int year) {
        validateYear(year);
        LocalDate from = LocalDate.of(year, 1, 1);
        Totals totals = aggregate(userId, from, from.plusYears(1));
        return new YearlyReportResponse(year, totals.income(), totals.expenses(), totals.netSavings());
    }

    private Totals aggregate(Long userId, LocalDate fromInclusive, LocalDate toExclusive) {
        Map<String, BigDecimal> income = new LinkedHashMap<>();
        Map<String, BigDecimal> expenses = new LinkedHashMap<>();
        BigDecimal net = MonetaryAmounts.ZERO;
        for (CategoryTotal row : transactionRepository.sumByCategory(userId, fromInclusive, toExclusive)) {
            BigDecimal amount = MonetaryAmounts.normalize(row.total());
            if (row.type() == TransactionType.INCOME) {
                income.merge(row.categoryName(), amount, BigDecimal::add);
                net = net.add(amount);
            } else {
                expenses.merge(row.categoryName(), amount, BigDecimal::add);
                net = net.subtract(amount);
            }
        }
        return new Totals(income, expenses, net);
    }

    private static void validateYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessRuleViolationException("Year must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
    }

    private record Totals(Map<String, BigDecimal> income, Map<String, BigDecimal> expenses, BigDecimal netSavings) {
    }
}
