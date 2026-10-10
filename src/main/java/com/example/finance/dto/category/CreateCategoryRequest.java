package com.example.finance.dto.category;

import com.example.finance.entity.enums.TransactionType;
import com.example.finance.util.InputNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a custom category. The name is trimmed on construction.
 *
 * @param name category name; letters, digits, spaces and {@code _ . & ' -}, starting with a letter or digit
 * @param type INCOME or EXPENSE
 */
public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 50, message = "Category name must be at most 50 characters")
        @Pattern(regexp = "^[\\p{L}\\p{N}][\\p{L}\\p{N} _.&'-]*$",
                message = "Category name may only contain letters, digits, spaces and _ . & ' -")
        String name,

        @NotNull(message = "Category type is required (INCOME or EXPENSE)")
        TransactionType type) {

    public CreateCategoryRequest {
        name = InputNormalizer.trim(name);
    }
}
