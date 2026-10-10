package com.example.finance.service;

import com.example.finance.entity.Category;
import com.example.finance.entity.SavingsGoal;
import com.example.finance.entity.Transaction;
import com.example.finance.entity.User;
import com.example.finance.entity.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Builders for entities with ids, as they would come back from the database.
 */
final class TestEntities {

    private TestEntities() {
    }

    static User user(long id) {
        User user = new User("user" + id + "@example.com", "hash", "User", "+1234567890");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    static Category defaultCategory(long id, String name, TransactionType type) {
        Category category = Category.custom(name, type, null);
        ReflectionTestUtils.setField(category, "defaultCategory", true);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    static Category customCategory(long id, String name, TransactionType type, User owner) {
        Category category = Category.custom(name, type, owner);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    static Transaction transaction(long id, User user, Category category, String amount, LocalDate date) {
        Transaction transaction = new Transaction(user, category, new BigDecimal(amount), date, null);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    static SavingsGoal goal(long id, User user, String target, LocalDate targetDate, LocalDate startDate) {
        SavingsGoal goal = new SavingsGoal(user, "Goal", new BigDecimal(target), targetDate, startDate);
        ReflectionTestUtils.setField(goal, "id", id);
        return goal;
    }
}
