package com.example.finance.service;

import com.example.finance.dto.transaction.CreateTransactionRequest;
import com.example.finance.dto.transaction.TransactionFilter;
import com.example.finance.dto.transaction.TransactionResponse;
import com.example.finance.dto.transaction.UpdateTransactionRequest;
import com.example.finance.entity.Category;
import com.example.finance.entity.Transaction;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.exception.BusinessRuleViolationException;
import com.example.finance.exception.ResourceNotFoundException;
import com.example.finance.mapper.TransactionMapper;
import com.example.finance.repository.TransactionRepository;
import com.example.finance.repository.TransactionSpecifications;
import com.example.finance.repository.UserRepository;
import com.example.finance.util.InputNormalizer;
import com.example.finance.util.MonetaryAmounts;
import com.example.finance.util.SupportedDateRange;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction CRUD. Every operation is scoped to the authenticated owner; another user's transaction is reported
 * as not found so its existence is not disclosed.
 */
@Service
public class TransactionService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final TransactionMapper transactionMapper;
    private final Clock clock;

    /**
     * @param transactionRepository transaction persistence
     * @param userRepository        owner references
     * @param categoryService       category resolution
     * @param transactionMapper     entity to DTO conversion
     * @param clock                 business clock
     */
    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository,
                              CategoryService categoryService, TransactionMapper transactionMapper, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
        this.transactionMapper = transactionMapper;
        this.clock = clock;
    }

    /**
     * @param userId  current user
     * @param request validated transaction data
     * @return the created transaction
     * @throws BusinessRuleViolationException for future dates, unknown categories or a conflicting type
     */
    @Transactional
    public TransactionResponse create(Long userId, CreateTransactionRequest request) {
        if (request.date().isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleViolationException("Transaction date cannot be in the future");
        }
        if (request.date().isBefore(SupportedDateRange.MIN)) {
            throw new BusinessRuleViolationException(
                    "Transaction date must be on or after " + SupportedDateRange.MIN);
        }
        Category category = categoryService.resolveAccessibleCategory(userId, request.category());
        ensureTypeMatches(request.type(), category);
        Transaction transaction = new Transaction(userRepository.getReferenceById(userId), category,
                MonetaryAmounts.normalize(request.amount()), request.date(), request.description());
        return transactionMapper.toResponse(saveAndFlush(transaction, category));
    }

    /**
     * @param userId current user
     * @param filter optional filters; both date bounds inclusive
     * @return matching transactions, newest first (ties broken by id, newest first)
     * @throws BusinessRuleViolationException if a date is outside the supported range, startDate is after endDate
     *                                        or categoryId is not positive
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> list(Long userId, TransactionFilter filter) {
        ensureSupported("startDate", filter.startDate());
        ensureSupported("endDate", filter.endDate());
        if (filter.startDate() != null && filter.endDate() != null && filter.startDate().isAfter(filter.endDate())) {
            throw new BusinessRuleViolationException("startDate must be on or before endDate");
        }
        if (filter.categoryId() != null && filter.categoryId() <= 0) {
            throw new BusinessRuleViolationException("categoryId must be a positive number");
        }
        return transactionRepository.findAll(TransactionSpecifications.ownedByAndMatching(userId, filter), NEWEST_FIRST)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    /**
     * Applies a partial update. The date is not part of the request and therefore never changes; a request with
     * nothing to change is a no-op that returns the current state.
     *
     * @param userId  current user
     * @param id      transaction id
     * @param request fields to change
     * @return the updated transaction
     * @throws ResourceNotFoundException      if the user has no such transaction
     * @throws BusinessRuleViolationException for an unknown category or a conflicting type
     */
    @Transactional
    public TransactionResponse update(Long userId, Long id, UpdateTransactionRequest request) {
        Transaction transaction = findOwned(userId, id);
        Category category = request.category() == null
                ? transaction.getCategory()
                : categoryService.resolveAccessibleCategory(userId, request.category());
        ensureTypeMatches(request.type(), category);

        transaction.setCategory(category);
        if (request.amount() != null) {
            transaction.setAmount(MonetaryAmounts.normalize(request.amount()));
        }
        if (request.description() != null) {
            transaction.setDescription(InputNormalizer.trimToNull(request.description()));
        }
        return transactionMapper.toResponse(saveAndFlush(transaction, category));
    }

    /**
     * @param userId current user
     * @param id     transaction id
     * @throws ResourceNotFoundException if the user has no such transaction
     */
    @Transactional
    public void delete(Long userId, Long id) {
        transactionRepository.delete(findOwned(userId, id));
    }

    private Transaction findOwned(Long userId, Long id) {
        return transactionRepository.findOwnedById(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + id + " not found"));
    }

    private Transaction saveAndFlush(Transaction transaction, Category category) {
        try {
            return transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            // The category was deleted concurrently; the foreign key rejected the write.
            throw new BusinessRuleViolationException("Category '" + category.getName() + "' does not exist");
        }
    }

    private static void ensureSupported(String parameter, LocalDate date) {
        if (date != null && !SupportedDateRange.contains(date)) {
            throw new BusinessRuleViolationException(parameter + " must be " + SupportedDateRange.describe());
        }
    }

    private static void ensureTypeMatches(TransactionType requestedType, Category category) {
        if (requestedType != null && requestedType != category.getType()) {
            throw new BusinessRuleViolationException("Transaction type " + requestedType
                    + " does not match category '" + category.getName() + "' of type " + category.getType());
        }
    }
}
