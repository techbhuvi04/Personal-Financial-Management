package com.example.finance.dto.transaction;

import java.util.List;

/**
 * Wrapper for the transaction listing.
 *
 * @param transactions transactions, newest first
 */
public record TransactionListResponse(List<TransactionResponse> transactions) {
}
