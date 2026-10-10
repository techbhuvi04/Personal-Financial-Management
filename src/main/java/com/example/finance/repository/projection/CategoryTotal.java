package com.example.finance.repository.projection;

import com.example.finance.entity.enums.TransactionType;
import java.math.BigDecimal;

/**
 * Sum of transaction amounts for one category.
 *
 * @param categoryName category display name
 * @param type         category type
 * @param total        sum of amounts
 */
public record CategoryTotal(String categoryName, TransactionType type, BigDecimal total) {
}
