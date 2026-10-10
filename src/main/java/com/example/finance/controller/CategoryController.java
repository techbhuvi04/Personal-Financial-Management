package com.example.finance.controller;

import com.example.finance.dto.category.CategoryListResponse;
import com.example.finance.dto.category.CategoryResponse;
import com.example.finance.dto.category.CreateCategoryRequest;
import com.example.finance.dto.common.MessageResponse;
import com.example.finance.security.CustomUserDetails;
import com.example.finance.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Category listing and custom category management.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * @param categoryService category business logic
     */
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * @param user authenticated user
     * @return default categories plus the user's custom categories
     */
    @GetMapping
    public CategoryListResponse list(@AuthenticationPrincipal CustomUserDetails user) {
        return new CategoryListResponse(categoryService.listCategories(user.getId()));
    }

    /**
     * @param user    authenticated user
     * @param request name and type
     * @return the created category
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal CustomUserDetails user,
                                   @Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.createCategory(user.getId(), request);
    }

    /**
     * @param user authenticated user
     * @param name custom category name (case-insensitive)
     * @return confirmation
     */
    @DeleteMapping("/{name}")
    public MessageResponse delete(@AuthenticationPrincipal CustomUserDetails user, @PathVariable String name) {
        categoryService.deleteCategory(user.getId(), name);
        return new MessageResponse("Category deleted successfully");
    }
}
