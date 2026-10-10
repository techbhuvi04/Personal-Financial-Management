package com.example.finance.dto.goal;

import java.util.List;

/**
 * Wrapper for the goal listing.
 *
 * @param goals the user's goals in creation order
 */
public record GoalListResponse(List<GoalResponse> goals) {
}
