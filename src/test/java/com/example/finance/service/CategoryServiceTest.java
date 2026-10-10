package com.example.finance.service;

import static com.example.finance.service.TestEntities.customCategory;
import static com.example.finance.service.TestEntities.defaultCategory;
import static com.example.finance.service.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.finance.dto.category.CategoryResponse;
import com.example.finance.dto.category.CreateCategoryRequest;
import com.example.finance.entity.Category;
import com.example.finance.entity.User;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.exception.BusinessRuleViolationException;
import com.example.finance.exception.DuplicateResourceException;
import com.example.finance.exception.ResourceForbiddenException;
import com.example.finance.exception.ResourceNotFoundException;
import com.example.finance.mapper.CategoryMapper;
import com.example.finance.repository.CategoryRepository;
import com.example.finance.repository.TransactionRepository;
import com.example.finance.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;

    private CategoryService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, transactionRepository, userRepository, new CategoryMapper());
        owner = user(USER_ID);
    }

    @Test
    void listMapsAccessibleCategories() {
        when(categoryRepository.findAllAccessible(USER_ID)).thenReturn(List.of(
                defaultCategory(1, "Salary", TransactionType.INCOME),
                customCategory(9, "Gifts", TransactionType.EXPENSE, owner)));

        assertThat(service.listCategories(USER_ID)).containsExactly(
                new CategoryResponse(1L, "Salary", TransactionType.INCOME, false),
                new CategoryResponse(9L, "Gifts", TransactionType.EXPENSE, true));
    }

    @Test
    void createPersistsCustomCategoryForOwner() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.createCategory(USER_ID,
                new CreateCategoryRequest("Side Hustle", TransactionType.INCOME));

        assertThat(response.name()).isEqualTo("Side Hustle");
        assertThat(response.isCustom()).isTrue();
    }

    @Test
    void createRejectsDefaultName() {
        when(categoryRepository.existsByDefaultCategoryTrueAndNormalizedName("food")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory(USER_ID,
                new CreateCategoryRequest("FOOD", TransactionType.EXPENSE)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("default category");
    }

    @Test
    void createRejectsExistingCustomName() {
        when(categoryRepository.existsByOwnerIdAndNormalizedName(USER_ID, "gifts")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory(USER_ID,
                new CreateCategoryRequest("Gifts", TransactionType.EXPENSE)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesConcurrentInsertIntoDuplicate() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenThrow(new DataIntegrityViolationException("x"));

        assertThatThrownBy(() -> service.createCategory(USER_ID,
                new CreateCategoryRequest("Gifts", TransactionType.EXPENSE)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deleteRemovesUnreferencedCustomCategory() {
        Category gifts = customCategory(9, "Gifts", TransactionType.EXPENSE, owner);
        when(categoryRepository.findOwnedForDeletion(USER_ID, "gifts")).thenReturn(Optional.of(gifts));

        service.deleteCategory(USER_ID, " GIFTS ");

        verify(categoryRepository).delete(gifts);
        verify(categoryRepository).flush();
    }

    @Test
    void deleteDefaultCategoryIsForbidden() {
        when(categoryRepository.findOwnedForDeletion(USER_ID, "salary")).thenReturn(Optional.empty());
        when(categoryRepository.existsByDefaultCategoryTrueAndNormalizedName("salary")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCategory(USER_ID, "Salary"))
                .isInstanceOf(ResourceForbiddenException.class);
    }

    @Test
    void deleteUnknownCategoryIsNotFound() {
        when(categoryRepository.findOwnedForDeletion(USER_ID, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCategory(USER_ID, "Nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category 'Nope' not found");
    }

    @Test
    void deleteReferencedCategoryIsRejected() {
        Category gifts = customCategory(9, "Gifts", TransactionType.EXPENSE, owner);
        when(categoryRepository.findOwnedForDeletion(USER_ID, "gifts")).thenReturn(Optional.of(gifts));
        when(transactionRepository.existsByCategoryId(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCategory(USER_ID, "Gifts"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteRaceWithNewTransactionIsRejectedByForeignKey() {
        Category gifts = customCategory(9, "Gifts", TransactionType.EXPENSE, owner);
        when(categoryRepository.findOwnedForDeletion(USER_ID, "gifts")).thenReturn(Optional.of(gifts));
        doThrow(new DataIntegrityViolationException("fk")).when(categoryRepository).flush();

        assertThatThrownBy(() -> service.deleteCategory(USER_ID, "Gifts"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("is used by existing transactions");
    }

    @Test
    void deleteBlankNameIsRejected() {
        assertThatThrownBy(() -> service.deleteCategory(USER_ID, "  "))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void resolvingADefaultCategoryTakesNoLock() {
        Category salary = defaultCategory(1, "Salary", TransactionType.INCOME);
        when(categoryRepository.findAccessibleByNormalizedName(USER_ID, "salary")).thenReturn(Optional.of(salary));

        assertThat(service.resolveAccessibleCategory(USER_ID, "SALARY")).isSameAs(salary);
        verify(categoryRepository, never()).findByIdForReference(any());
    }

    @Test
    void resolvingACustomCategoryLocksItAgainstConcurrentDeletion() {
        Category gifts = customCategory(9, "Gifts", TransactionType.EXPENSE, owner);
        when(categoryRepository.findAccessibleByNormalizedName(USER_ID, "gifts")).thenReturn(Optional.of(gifts));
        when(categoryRepository.findByIdForReference(9L)).thenReturn(Optional.of(gifts));

        assertThat(service.resolveAccessibleCategory(USER_ID, "Gifts")).isSameAs(gifts);
        verify(categoryRepository).findByIdForReference(9L);
    }

    @Test
    void customCategoryDeletedBeforeTheLockIsTakenIsReportedAsMissing() {
        Category gifts = customCategory(9, "Gifts", TransactionType.EXPENSE, owner);
        when(categoryRepository.findAccessibleByNormalizedName(USER_ID, "gifts")).thenReturn(Optional.of(gifts));
        when(categoryRepository.findByIdForReference(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAccessibleCategory(USER_ID, "Gifts"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Category 'Gifts' does not exist");
    }

    @Test
    void resolveAccessibleCategoryFailsForUnknownName() {
        when(categoryRepository.findAccessibleByNormalizedName(USER_ID, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAccessibleCategory(USER_ID, "Nope"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Category 'Nope' does not exist");
    }
}
