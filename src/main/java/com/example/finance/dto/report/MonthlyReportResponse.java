package com.example.finance.dto.report;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Income and expenses for one calendar month.
 *
 * @param month         1-12
 * @param year          calendar year
 * @param totalIncome   income per category name, ordered case-insensitively by name
 * @param totalExpenses expenses per category name, ordered case-insensitively by name
 * @param netSavings    total income minus total expenses
 */
public record MonthlyReportResponse(int month, int year, Map<String, BigDecimal> totalIncome,
                                    Map<String, BigDecimal> totalExpenses, BigDecimal netSavings) {
}
