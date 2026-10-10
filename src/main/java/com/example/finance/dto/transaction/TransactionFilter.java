package com.example.finance.dto.transaction;

import com.example.finance.entity.enums.TransactionType;
import java.time.LocalDate;

/**
 * Optional listing filters. Null fields are not applied; all non-null filters are combined with AND.
 * Both date bounds are inclusive.
 *
 * @param startDate    earliest transaction date
 * @param endDate      latest transaction date
 * @param categoryId   category id
 * @param categoryName category name (case-insensitive)
 * @param type         income or expense
 */
public record TransactionFilter(LocalDate startDate, LocalDate endDate, Long categoryId, String categoryName,
                                TransactionType type) {
}
