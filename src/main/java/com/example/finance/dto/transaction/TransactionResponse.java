package com.example.finance.dto.transaction;

import com.example.finance.entity.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Transaction representation.
 *
 * @param id          transaction id
 * @param amount      amount with two decimal places
 * @param date        transaction date
 * @param category    category name
 * @param description optional description
 * @param type        derived from the category
 */
public record TransactionResponse(Long id, BigDecimal amount, LocalDate date, String category, String description,
                                  TransactionType type) {
}
