package com.example.finance.repository;

import com.example.finance.entity.Category;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence operations for {@link Category}. "Accessible" means a default category or a custom category owned
 * by the given user.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * @param userId current user
     * @return default categories followed by the user's custom categories, each group in creation order
     */
    @Query("""
            select c from Category c
            where c.owner is null or c.owner.id = :userId
            order by c.defaultCategory desc, c.id asc
            """)
    List<Category> findAllAccessible(@Param("userId") Long userId);

    /**
     * @param userId         current user
     * @param normalizedName trimmed, lower-cased category name
     * @return the accessible category with that name, if any
     */
    @Query("""
            select c from Category c
            where c.normalizedName = :normalizedName and (c.owner is null or c.owner.id = :userId)
            """)
    Optional<Category> findAccessibleByNormalizedName(@Param("userId") Long userId,
                                                      @Param("normalizedName") String normalizedName);

    /**
     * Loads one of the user's custom categories with an exclusive row lock, held until the caller's transaction
     * ends, so no transaction can start referencing it while it is being checked and deleted.
     *
     * @param userId         owner
     * @param normalizedName trimmed, lower-cased category name
     * @return the locked category, if the user has one with that name
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.owner.id = :userId and c.normalizedName = :normalizedName")
    Optional<Category> findOwnedForDeletion(@Param("userId") Long userId,
                                            @Param("normalizedName") String normalizedName);

    /**
     * Re-reads a category with a shared row lock held until the caller's transaction ends, so the category cannot
     * be deleted while a transaction referencing it is being written.
     *
     * @param id category id
     * @return the locked category, or empty if it was deleted in the meantime
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findByIdForReference(@Param("id") Long id);

    /**
     * @param normalizedName trimmed, lower-cased category name
     * @return whether a default category uses this name
     */
    boolean existsByDefaultCategoryTrueAndNormalizedName(String normalizedName);

    /**
     * @param userId         owner
     * @param normalizedName trimmed, lower-cased category name
     * @return whether the user already has a custom category with this name
     */
    boolean existsByOwnerIdAndNormalizedName(Long userId, String normalizedName);
}
