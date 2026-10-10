package com.example.finance.controller;

import com.example.finance.dto.common.MessageResponse;
import com.example.finance.dto.goal.CreateGoalRequest;
import com.example.finance.dto.goal.GoalListResponse;
import com.example.finance.dto.goal.GoalResponse;
import com.example.finance.dto.goal.UpdateGoalRequest;
import com.example.finance.security.CustomUserDetails;
import com.example.finance.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Savings goal management for the authenticated user.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    /**
     * @param goalService goal business logic
     */
    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * @param user    authenticated user
     * @param request goal data
     * @return the created goal with progress
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthenticationPrincipal CustomUserDetails user,
                               @Valid @RequestBody CreateGoalRequest request) {
        return goalService.create(user.getId(), request);
    }

    /**
     * @param user authenticated user
     * @return the user's goals with progress
     */
    @GetMapping
    public GoalListResponse list(@AuthenticationPrincipal CustomUserDetails user) {
        return new GoalListResponse(goalService.list(user.getId()));
    }

    /**
     * @param user authenticated user
     * @param id   goal id
     * @return the goal with progress
     */
    @GetMapping("/{id}")
    public GoalResponse get(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return goalService.get(user.getId(), id);
    }

    /**
     * @param user    authenticated user
     * @param id      goal id
     * @param request new target amount and/or date
     * @return the updated goal with progress
     */
    @PutMapping("/{id}")
    public GoalResponse update(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
                               @Valid @RequestBody UpdateGoalRequest request) {
        return goalService.update(user.getId(), id, request);
    }

    /**
     * @param user authenticated user
     * @param id   goal id
     * @return confirmation
     */
    @DeleteMapping("/{id}")
    public MessageResponse delete(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        goalService.delete(user.getId(), id);
        return new MessageResponse("Goal deleted successfully");
    }
}
