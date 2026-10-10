package com.example.finance.mapper;

import com.example.finance.dto.transaction.TransactionResponse;
import com.example.finance.entity.Transaction;
import com.example.finance.util.MonetaryAmounts;
import org.springframework.stereotype.Component;

/**
 * Converts {@link Transaction} entities to API representations.
 */
@Component
public class TransactionMapper {

    /**
     * @param transaction entity with its category loaded
     * @return response DTO
     */
    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                MonetaryAmounts.normalize(transaction.getAmount()),
                transaction.getDate(),
                transaction.getCategory().getName(),
                transaction.getDescription(),
                transaction.getType());
    }
}
