package com.example.finance.repository.projection;

import com.example.finance.entity.enums.TransactionType;
import java.math.BigDecimal;

/**
 * Sum of transaction amounts for one transaction type.
 *
 * @param type  income or expense
 * @param total sum of amounts
 */
public record TypeTotal(TransactionType type, BigDecimal total) {
}
