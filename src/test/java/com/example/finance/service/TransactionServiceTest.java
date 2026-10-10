package com.example.finance.service;

import static com.example.finance.service.TestEntities.defaultCategory;
import static com.example.finance.service.TestEntities.transaction;
import static com.example.finance.service.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.finance.dto.transaction.CreateTransactionRequest;
import com.example.finance.dto.transaction.TransactionFilter;
import com.example.finance.dto.transaction.TransactionResponse;
import com.example.finance.dto.transaction.UpdateTransactionRequest;
import com.example.finance.entity.Category;
import com.example.finance.entity.Transaction;
import com.example.finance.entity.User;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.exception.BusinessRuleViolationException;
import com.example.finance.exception.ResourceNotFoundException;
import com.example.finance.mapper.TransactionMapper;
import com.example.finance.repository.TransactionRepository;
import com.example.finance.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryService categoryService;

    private TransactionService service;
    private User owner;
    private Category salary;
    private Category food;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T23:59:59Z"), ZoneOffset.UTC);
        service = new TransactionService(transactionRepository, userRepository, categoryService,
                new TransactionMapper(), clock);
        owner = user(USER_ID);
        salary = defaultCategory(1, "Salary", TransactionType.INCOME);
        food = defaultCategory(2, "Food", TransactionType.EXPENSE);
    }

    @Test
    void createNormalizesAmountAndDerivesType() {
        when(categoryService.resolveAccessibleCategory(USER_ID, "Salary")).thenReturn(salary);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = service.create(USER_ID,
                new CreateTransactionRequest(new BigDecimal("50000"), TODAY, "Salary", "Pay", null));

        assertThat(response.amount()).isEqualByComparingTo("50000").hasToString("50000.00");
        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.date()).isEqualTo(TODAY);
    }

    @Test
    void createRejectsFutureDateBeforeTouchingCategories() {
        assertThatThrownBy(() -> service.create(USER_ID,
                new CreateTransactionRequest(BigDecimal.ONE, TODAY.plusDays(1), "Food", null, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Transaction date cannot be in the future");
        verifyNoInteractions(categoryService);
    }

    @Test
    void createRejectsTypeMismatch() {
        when(categoryService.resolveAccessibleCategory(USER_ID, "Food")).thenReturn(food);

        assertThatThrownBy(() -> service.create(USER_ID,
                new CreateTransactionRequest(BigDecimal.ONE, TODAY, "Food", null, TransactionType.INCOME)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesForeignKeyRaceIntoBadRequest() {
        when(categoryService.resolveAccessibleCategory(USER_ID, "Food")).thenReturn(food);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("fk"));

        assertThatThrownBy(() -> service.create(USER_ID,
                new CreateTransactionRequest(BigDecimal.ONE, TODAY, "Food", null, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Category 'Food' does not exist");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listQueriesNewestFirst() {
        when(transactionRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Order.desc("date"),
                Sort.Order.desc("id"))))).thenReturn(List.of(transaction(5, owner, food, "10", TODAY)));

        assertThat(service.list(USER_ID, new TransactionFilter(null, null, null, null, null)))
                .extracting(TransactionResponse::id).containsExactly(5L);
    }

    @Test
    void listRejectsInvertedDateRangeAndNonPositiveCategoryId() {
        assertThatThrownBy(() -> service.list(USER_ID,
                new TransactionFilter(TODAY, TODAY.minusDays(1), null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.list(USER_ID, new TransactionFilter(null, null, -1L, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void updateRequiresAtLeastOneUpdatableField() {
        assertThatThrownBy(() -> service.update(USER_ID, 5L,
                new UpdateTransactionRequest(null, null, null, TODAY, TransactionType.EXPENSE)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void updateOfMissingOrForeignTransactionIsNotFound() {
        when(transactionRepository.findOwnedById(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, 5L,
                new UpdateTransactionRequest(BigDecimal.ONE, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRejectsChangedDate() {
        when(transactionRepository.findOwnedById(5L, USER_ID))
                .thenReturn(Optional.of(transaction(5, owner, food, "10", TODAY)));

        assertThatThrownBy(() -> service.update(USER_ID, 5L,
                new UpdateTransactionRequest(BigDecimal.ONE, null, null, TODAY.minusDays(1), null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Transaction date cannot be changed");
    }

    @Test
    void updateAppliesCategoryAmountAndClearsBlankDescription() {
        Transaction existing = transaction(5, owner, food, "10", TODAY);
        existing.setDescription("old");
        when(transactionRepository.findOwnedById(5L, USER_ID)).thenReturn(Optional.of(existing));
        when(categoryService.resolveAccessibleCategory(USER_ID, "Salary")).thenReturn(salary);
        when(transactionRepository.saveAndFlush(existing)).thenReturn(existing);

        TransactionResponse response = service.update(USER_ID, 5L,
                new UpdateTransactionRequest(new BigDecimal("12.5"), "Salary", "  ", TODAY, TransactionType.INCOME));

        assertThat(response.amount()).hasToString("12.50");
        assertThat(response.category()).isEqualTo("Salary");
        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.description()).isNull();
    }

    @Test
    void deleteRemovesOwnedTransaction() {
        Transaction existing = transaction(5, owner, food, "10", TODAY);
        when(transactionRepository.findOwnedById(5L, USER_ID)).thenReturn(Optional.of(existing));

        service.delete(USER_ID, 5L);

        verify(transactionRepository).delete(existing);
    }
}
