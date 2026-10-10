package com.example.finance.service;

import com.example.finance.dto.category.CategoryResponse;
import com.example.finance.dto.category.CreateCategoryRequest;
import com.example.finance.entity.Category;
import com.example.finance.exception.BusinessRuleViolationException;
import com.example.finance.exception.DuplicateResourceException;
import com.example.finance.exception.ResourceForbiddenException;
import com.example.finance.exception.ResourceNotFoundException;
import com.example.finance.mapper.CategoryMapper;
import com.example.finance.repository.CategoryRepository;
import com.example.finance.repository.TransactionRepository;
import com.example.finance.repository.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default and custom category management. Names are compared case-insensitively; a custom category may not reuse
 * a default category's name or another of the same user's custom names.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryMapper categoryMapper;

    /**
     * @param categoryRepository    category persistence
     * @param transactionRepository used to check whether a category is referenced
     * @param userRepository        owner references
     * @param categoryMapper        entity to DTO conversion
     */
    public CategoryService(CategoryRepository categoryRepository, TransactionRepository transactionRepository,
                           UserRepository userRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.categoryMapper = categoryMapper;
    }

    /**
     * @param userId current user
     * @return default categories followed by the user's custom categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(Long userId) {
        return categoryRepository.findAllAccessible(userId).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    /**
     * @param userId  current user
     * @param request validated name and type
     * @return the created category
     * @throws DuplicateResourceException if the name clashes with a default or existing custom category
     */
    @Transactional
    public CategoryResponse createCategory(Long userId, CreateCategoryRequest request) {
        String normalizedName = Category.normalize(request.name());
        if (categoryRepository.existsByDefaultCategoryTrueAndNormalizedName(normalizedName)) {
            throw new DuplicateResourceException(
                    "Category '" + request.name() + "' already exists as a default category");
        }
        if (categoryRepository.existsByOwnerIdAndNormalizedName(userId, normalizedName)) {
            throw duplicateCustom(request.name());
        }
        Category category = Category.custom(request.name(), request.type(), userRepository.getReferenceById(userId));
        try {
            return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request created the same name between the check and the insert.
            throw duplicateCustom(request.name());
        }
    }

    /**
     * Deletes one of the user's custom categories if no transaction references it.
     *
     * @param userId current user
     * @param name   category name (case-insensitive)
     * @throws ResourceForbiddenException     for default categories
     * @throws ResourceNotFoundException      if the user has no such category
     * @throws BusinessRuleViolationException if transactions still reference the category
     */
    @Transactional
    public void deleteCategory(Long userId, String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleViolationException("Category name is required");
        }
        String normalizedName = Category.normalize(name);
        Category category = categoryRepository.findByOwnerIdAndNormalizedName(userId, normalizedName)
                .orElseThrow(() -> categoryRepository.existsByDefaultCategoryTrueAndNormalizedName(normalizedName)
                        ? new ResourceForbiddenException("Default categories cannot be deleted")
                        : new ResourceNotFoundException("Category '" + name.trim() + "' not found"));
        if (transactionRepository.existsByCategoryId(category.getId())) {
            throw categoryInUse(category.getName());
        }
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // A transaction referencing the category was committed concurrently; the foreign key protects it.
            throw categoryInUse(category.getName());
        }
    }

    /**
     * Resolves a category name to a default category or one of the user's custom categories.
     *
     * @param userId current user
     * @param name   category name (case-insensitive)
     * @return the category
     * @throws BusinessRuleViolationException if no such category is accessible to the user
     */
    @Transactional(readOnly = true)
    public Category resolveAccessibleCategory(Long userId, String name) {
        return categoryRepository.findAccessibleByNormalizedName(userId, Category.normalize(name))
                .orElseThrow(() -> new BusinessRuleViolationException("Category '" + name + "' does not exist"));
    }

    private static DuplicateResourceException duplicateCustom(String name) {
        return new DuplicateResourceException("Category '" + name + "' already exists");
    }

    private static BusinessRuleViolationException categoryInUse(String name) {
        return new BusinessRuleViolationException(
                "Category '" + name + "' is used by existing transactions and cannot be deleted");
    }
}
