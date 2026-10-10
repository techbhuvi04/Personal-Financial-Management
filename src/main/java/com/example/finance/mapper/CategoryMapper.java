package com.example.finance.mapper;

import com.example.finance.dto.category.CategoryResponse;
import com.example.finance.entity.Category;
import org.springframework.stereotype.Component;

/**
 * Converts {@link Category} entities to API representations.
 */
@Component
public class CategoryMapper {

    /**
     * @param category entity
     * @return response DTO
     */
    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType(), category.isCustom());
    }
}
