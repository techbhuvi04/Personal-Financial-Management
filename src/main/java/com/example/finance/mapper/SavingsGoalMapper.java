package com.example.finance.mapper;

import com.example.finance.dto.goal.GoalResponse;
import com.example.finance.entity.SavingsGoal;
import com.example.finance.service.GoalProgress;
import com.example.finance.util.MonetaryAmounts;
import org.springframework.stereotype.Component;

/**
 * Converts {@link SavingsGoal} entities plus their computed progress to API representations.
 */
@Component
public class SavingsGoalMapper {

    /**
     * @param goal     entity
     * @param progress progress computed for the goal
     * @return response DTO
     */
    public GoalResponse toResponse(SavingsGoal goal, GoalProgress progress) {
        return new GoalResponse(
                goal.getId(),
                goal.getGoalName(),
                MonetaryAmounts.normalize(goal.getTargetAmount()),
                goal.getTargetDate(),
                goal.getStartDate(),
                progress.currentProgress(),
                // Computed exactly in BigDecimal; converted only for presentation.
                progress.progressPercentage().doubleValue(),
                progress.remainingAmount());
    }
}
