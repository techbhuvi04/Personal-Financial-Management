package com.example.finance.repository;

import com.example.finance.entity.Transaction;
import com.example.finance.repository.projection.CategoryTotal;
import com.example.finance.repository.projection.TypeTotal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence and aggregation operations for {@link Transaction}. Every query is scoped to an owner.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * @param id     transaction id
     * @param userId owner
     * @return the transaction with its category, only if it belongs to the user
     */
    @Query("select t from Transaction t join fetch t.category where t.id = :id and t.user.id = :userId")
    Optional<Transaction> findOwnedById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * @param categoryId category id
     * @return whether any transaction references the category
     */
    boolean existsByCategoryId(Long categoryId);

    /**
     * Sums amounts per category for a half-open date range {@code [fromInclusive, toExclusive)}.
     *
     * @param userId        owner
     * @param fromInclusive first date included
     * @param toExclusive   first date excluded
     * @return one row per category that has transactions in the range, ordered by category name
     */
    @Query("""
            select new com.example.finance.repository.projection.CategoryTotal(c.name, c.type, sum(t.amount))
            from Transaction t join t.category c
            where t.user.id = :userId and t.date >= :fromInclusive and t.date < :toExclusive
            group by c.id, c.name, c.type
            order by c.name asc
            """)
    List<CategoryTotal> sumByCategory(@Param("userId") Long userId,
                                      @Param("fromInclusive") LocalDate fromInclusive,
                                      @Param("toExclusive") LocalDate toExclusive);

    /**
     * Sums amounts per transaction type for a closed date range {@code [fromInclusive, toInclusive]}.
     *
     * @param userId        owner
     * @param fromInclusive first date included
     * @param toInclusive   last date included
     * @return at most one row per type
     */
    @Query("""
            select new com.example.finance.repository.projection.TypeTotal(c.type, sum(t.amount))
            from Transaction t join t.category c
            where t.user.id = :userId and t.date >= :fromInclusive and t.date <= :toInclusive
            group by c.type
            """)
    List<TypeTotal> sumByType(@Param("userId") Long userId,
                              @Param("fromInclusive") LocalDate fromInclusive,
                              @Param("toInclusive") LocalDate toInclusive);
}
