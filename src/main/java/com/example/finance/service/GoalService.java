package com.example.finance.service;

import com.example.finance.dto.goal.CreateGoalRequest;
import com.example.finance.dto.goal.GoalResponse;
import com.example.finance.dto.goal.UpdateGoalRequest;
import com.example.finance.entity.SavingsGoal;
import com.example.finance.exception.BusinessRuleViolationException;
import com.example.finance.exception.ResourceForbiddenException;
import com.example.finance.exception.ResourceNotFoundException;
import com.example.finance.mapper.SavingsGoalMapper;
import com.example.finance.repository.SavingsGoalRepository;
import com.example.finance.repository.UserRepository;
import com.example.finance.util.MonetaryAmounts;
import com.example.finance.util.SupportedDateRange;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Savings goal management. Accessing another user's goal yields 403, as specified by the assignment for goal
 * endpoints.
 */
@Service
public class GoalService {

    private final SavingsGoalRepository goalRepository;
    private final UserRepository userRepository;
    private final GoalProgressCalculator progressCalculator;
    private final SavingsGoalMapper goalMapper;
    private final Clock clock;

    /**
     * @param goalRepository     goal persistence
     * @param userRepository     owner references
     * @param progressCalculator shared progress computation
     * @param goalMapper         entity to DTO conversion
     * @param clock              business clock
     */
    public GoalService(SavingsGoalRepository goalRepository, UserRepository userRepository,
                       GoalProgressCalculator progressCalculator, SavingsGoalMapper goalMapper, Clock clock) {
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
        this.progressCalculator = progressCalculator;
        this.goalMapper = goalMapper;
        this.clock = clock;
    }

    /**
     * @param userId  current user
     * @param request validated goal data; start date defaults to today
     * @return the created goal with progress
     * @throws BusinessRuleViolationException if the target date is not in the future or the start date is
     */
    @Transactional
    public GoalResponse create(Long userId, CreateGoalRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = request.startDate() != null ? request.startDate() : today;
        if (startDate.isAfter(today)) {
            throw new BusinessRuleViolationException("Start date cannot be in the future");
        }
        if (startDate.isBefore(SupportedDateRange.MIN)) {
            throw new BusinessRuleViolationException("Start date must be on or after " + SupportedDateRange.MIN);
        }
        ensureFutureTargetDate(request.targetDate(), today);
        SavingsGoal goal = goalRepository.save(new SavingsGoal(userRepository.getReferenceById(userId),
                request.goalName(), MonetaryAmounts.normalize(request.targetAmount()), request.targetDate(),
                startDate));
        return toResponse(goal);
    }

    /**
     * @param userId current user
     * @return the user's goals with progress, in creation order
     */
    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId) {
        return goalRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * @param userId current user
     * @param id     goal id
     * @return the goal with progress
     */
    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long id) {
        return toResponse(findOwned(userId, id));
    }

    /**
     * Updates the target amount and/or target date; progress is recomputed immediately. Absent fields are left
     * unchanged, so a request with nothing to change returns the current state.
     *
     * @param userId  current user
     * @param id      goal id
     * @param request fields to change
     * @return the updated goal with progress
     */
    @Transactional
    public GoalResponse update(Long userId, Long id, UpdateGoalRequest request) {
        if (request.targetDate() != null) {
            ensureFutureTargetDate(request.targetDate(), LocalDate.now(clock));
        }
        SavingsGoal goal = findOwned(userId, id);
        if (request.targetAmount() != null) {
            goal.setTargetAmount(MonetaryAmounts.normalize(request.targetAmount()));
        }
        if (request.targetDate() != null) {
            goal.setTargetDate(request.targetDate());
        }
        return toResponse(goalRepository.saveAndFlush(goal));
    }

    /**
     * Deletes a goal; transactions are unaffected.
     *
     * @param userId current user
     * @param id     goal id
     */
    @Transactional
    public void delete(Long userId, Long id) {
        goalRepository.delete(findOwned(userId, id));
    }

    private SavingsGoal findOwned(Long userId, Long id) {
        SavingsGoal goal = goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal " + id + " not found"));
        if (!goal.isOwnedBy(userId)) {
            throw new ResourceForbiddenException("You do not have access to goal " + id);
        }
        return goal;
    }

    private GoalResponse toResponse(SavingsGoal goal) {
        return goalMapper.toResponse(goal, progressCalculator.calculate(goal));
    }

    private static void ensureFutureTargetDate(LocalDate targetDate, LocalDate today) {
        if (!targetDate.isAfter(today)) {
            throw new BusinessRuleViolationException("Target date must be in the future");
        }
        if (targetDate.isAfter(SupportedDateRange.MAX)) {
            throw new BusinessRuleViolationException("Target date must be on or before " + SupportedDateRange.MAX);
        }
    }
}
