package com.example.finance.service;

import com.example.finance.entity.SavingsGoal;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.repository.TransactionRepository;
import com.example.finance.repository.projection.TypeTotal;
import com.example.finance.util.MonetaryAmounts;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for goal progress: (total income - total expenses) of the goal owner's transactions
 * dated from the goal's start date through today, inclusive. Used by both goal listing and goal detail so they
 * cannot disagree.
 */
@Component
public class GoalProgressCalculator {

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    /**
     * @param transactionRepository aggregation queries
     * @param clock                 business clock defining "today"
     */
    public GoalProgressCalculator(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /**
     * @param goal goal whose owner, start date and target are used
     * @return computed progress
     */
    public GoalProgress calculate(SavingsGoal goal) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal net = MonetaryAmounts.ZERO;
        if (!goal.getStartDate().isAfter(today)) {
            for (TypeTotal total : transactionRepository.sumByType(goal.getUser().getId(), goal.getStartDate(), today)) {
                BigDecimal amount = MonetaryAmounts.normalize(total.total());
                net = total.type() == TransactionType.INCOME ? net.add(amount) : net.subtract(amount);
            }
        }
        return GoalProgress.of(net, goal.getTargetAmount());
    }
}
