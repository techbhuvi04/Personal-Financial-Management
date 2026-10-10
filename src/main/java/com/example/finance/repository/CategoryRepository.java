package com.example.finance.repository;

import com.example.finance.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * @param userId         owner
     * @param normalizedName trimmed, lower-cased category name
     * @return the user's custom category with that name, if any
     */
    Optional<Category> findByOwnerIdAndNormalizedName(Long userId, String normalizedName);

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
