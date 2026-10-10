package com.example.finance.entity.enums;

/**
 * Direction of money flow. A category has exactly one type, and every transaction inherits the type of its
 * category, so the same enum describes both.
 */
public enum TransactionType {
    INCOME,
    EXPENSE
}
