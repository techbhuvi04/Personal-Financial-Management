package com.example.finance.repository;

import com.example.finance.dto.transaction.TransactionFilter;
import com.example.finance.entity.Category;
import com.example.finance.entity.Transaction;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the dynamic query for the transaction listing. Each optional filter contributes one predicate, so new
 * filters can be added without touching existing ones.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * @param userId owner; always applied
     * @param filter optional filters; null fields are ignored
     * @return a specification that also fetches the category to avoid N+1 queries
     */
    @SuppressWarnings("unchecked")
    public static Specification<Transaction> ownedByAndMatching(Long userId, TransactionFilter filter) {
        return (root, query, cb) -> {
            Join<Transaction, Category> category;
            if (Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType())) {
                category = root.join("category", JoinType.INNER);
            } else {
                // Hibernate's fetch is also a join, so predicates can reference it without a second join.
                category = (Join<Transaction, Category>) root.<Transaction, Category>fetch("category", JoinType.INNER);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (filter.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), filter.startDate()));
            }
            if (filter.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), filter.endDate()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(category.get("id"), filter.categoryId()));
            }
            if (filter.categoryName() != null) {
                predicates.add(cb.equal(category.get("normalizedName"), Category.normalize(filter.categoryName())));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(category.get("type"), filter.type()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
