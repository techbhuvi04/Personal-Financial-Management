package com.example.finance.repository;

import com.example.finance.entity.SavingsGoal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence operations for {@link SavingsGoal}.
 */
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    /**
     * @param userId owner
     * @return the user's goals in creation order
     */
    List<SavingsGoal> findByUserIdOrderByIdAsc(Long userId);
}
