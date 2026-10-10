package com.example.finance.controller;

import com.example.finance.dto.common.MessageResponse;
import com.example.finance.dto.transaction.CreateTransactionRequest;
import com.example.finance.dto.transaction.TransactionFilter;
import com.example.finance.dto.transaction.TransactionListResponse;
import com.example.finance.dto.transaction.TransactionResponse;
import com.example.finance.dto.transaction.UpdateTransactionRequest;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.security.CustomUserDetails;
import com.example.finance.service.TransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transaction CRUD for the authenticated user.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * @param transactionService transaction business logic
     */
    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * @param user    authenticated user
     * @param request transaction data
     * @return the created transaction
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal CustomUserDetails user,
                                      @Valid @RequestBody CreateTransactionRequest request) {
        return transactionService.create(user.getId(), request);
    }

    /**
     * Lists the user's transactions, newest first. All filters are optional and combined with AND.
     *
     * @param user       authenticated user
     * @param startDate  earliest date, inclusive
     * @param endDate    latest date, inclusive
     * @param categoryId category id
     * @param category   category name (case-insensitive)
     * @param type       INCOME or EXPENSE
     * @return wrapped transaction list
     */
    @GetMapping
    public TransactionListResponse list(@AuthenticationPrincipal CustomUserDetails user,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) TransactionType type) {
        String categoryName = category == null || category.isBlank() ? null : category.trim();
        TransactionFilter filter = new TransactionFilter(startDate, endDate, categoryId, categoryName, type);
        return new TransactionListResponse(transactionService.list(user.getId(), filter));
    }

    /**
     * @param user    authenticated user
     * @param id      transaction id
     * @param request fields to change (date cannot change)
     * @return the updated transaction
     */
    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
                                      @Valid @RequestBody UpdateTransactionRequest request) {
        return transactionService.update(user.getId(), id, request);
    }

    /**
     * @param user authenticated user
     * @param id   transaction id
     * @return confirmation
     */
    @DeleteMapping("/{id}")
    public MessageResponse delete(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        transactionService.delete(user.getId(), id);
        return new MessageResponse("Transaction deleted successfully");
    }
}
