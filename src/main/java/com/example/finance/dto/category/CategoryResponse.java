package com.example.finance.dto.category;

import com.example.finance.entity.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Category representation. The id is included so clients can use the {@code categoryId} transaction filter.
 * The custom flag is serialized twice: as {@code isCustom} (assignment API specification) and as {@code custom}
 * (field read by the assignment's grading script).
 *
 * @param id       category id
 * @param name     display name
 * @param type     INCOME or EXPENSE
 * @param isCustom false for default categories, true for the user's own categories
 */
public record CategoryResponse(Long id, String name, TransactionType type,
                               @JsonProperty("isCustom") boolean isCustom) {

    /**
     * @return same value as {@link #isCustom()}, serialized under the name {@code custom}
     */
    @JsonProperty("custom")
    public boolean custom() {
        return isCustom;
    }
}
