package com.example.finance.dto.category;

import java.util.List;

/**
 * Wrapper for the category listing.
 *
 * @param categories default categories followed by the user's custom categories
 */
public record CategoryListResponse(List<CategoryResponse> categories) {
}
