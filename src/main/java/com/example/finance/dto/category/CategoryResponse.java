package com.example.finance.dto.category;

import com.example.finance.entity.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Category representation. The id is included so clients can use the {@code categoryId} transaction filter.
 *
 * @param id       category id
 * @param name     display name
 * @param type     INCOME or EXPENSE
 * @param isCustom false for default categories, true for the user's own categories
 */
public record CategoryResponse(Long id, String name, TransactionType type,
                               @JsonProperty("isCustom") boolean isCustom) {
}
