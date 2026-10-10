package com.example.finance.service;

import static com.example.finance.service.TestEntities.goal;
import static com.example.finance.service.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.finance.dto.goal.CreateGoalRequest;
import com.example.finance.dto.goal.GoalResponse;
import com.example.finance.dto.goal.UpdateGoalRequest;
import com.example.finance.entity.SavingsGoal;
import com.example.finance.entity.User;
import com.example.finance.exception.BusinessRuleViolationException;
import com.example.finance.exception.ResourceForbiddenException;
import com.example.finance.exception.ResourceNotFoundException;
import com.example.finance.mapper.SavingsGoalMapper;
import com.example.finance.repository.SavingsGoalRepository;
import com.example.finance.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final GoalProgress PROGRESS = GoalProgress.of(new BigDecimal("100"), new BigDecimal("1000"));

    @Mock
    private SavingsGoalRepository goalRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GoalProgressCalculator progressCalculator;

    private GoalService service;
    private User owner;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);
        service = new GoalService(goalRepository, userRepository, progressCalculator, new SavingsGoalMapper(), clock);
        owner = user(USER_ID);
    }

    @Test
    void createDefaultsStartDateToTodayAndReturnsProgress() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(goalRepository.save(any(SavingsGoal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(progressCalculator.calculate(any())).thenReturn(PROGRESS);

        GoalResponse response = service.create(USER_ID,
                new CreateGoalRequest("Fund", new BigDecimal("1000"), TODAY.plusDays(1), null));

        assertThat(response.startDate()).isEqualTo(TODAY);
        assertThat(response.targetAmount()).hasToString("1000.00");
        assertThat(response.progressPercentage()).hasToString("10.00");
    }

    @Test
    void createRejectsFutureStartAndNonFutureTarget() {
        assertThatThrownBy(() -> service.create(USER_ID,
                new CreateGoalRequest("Fund", BigDecimal.TEN, TODAY.plusYears(1), TODAY.plusDays(1))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Start date cannot be in the future");
        assertThatThrownBy(() -> service.create(USER_ID,
                new CreateGoalRequest("Fund", BigDecimal.TEN, TODAY, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Target date must be in the future");
        verifyNoInteractions(goalRepository);
    }

    @Test
    void listMapsEveryGoalWithItsOwnProgress() {
        SavingsGoal first = goal(1, owner, "1000", TODAY.plusYears(1), TODAY);
        SavingsGoal second = goal(2, owner, "500", TODAY.plusYears(1), TODAY.minusMonths(1));
        when(goalRepository.findByUserIdOrderByIdAsc(USER_ID)).thenReturn(List.of(first, second));
        when(progressCalculator.calculate(first)).thenReturn(GoalProgress.of(BigDecimal.ZERO, first.getTargetAmount()));
        when(progressCalculator.calculate(second)).thenReturn(GoalProgress.of(BigDecimal.TEN, second.getTargetAmount()));

        assertThat(service.list(USER_ID)).extracting(GoalResponse::currentProgress)
                .map(BigDecimal::toPlainString).containsExactly("0.00", "10.00");
    }

    @Test
    void getEnforcesOwnership() {
        when(goalRepository.findById(7L)).thenReturn(Optional.of(goal(7, user(2), "10", TODAY.plusDays(5), TODAY)));
        when(goalRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID, 7L)).isInstanceOf(ResourceForbiddenException.class);
        assertThatThrownBy(() -> service.get(USER_ID, 8L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesTargetFieldsOnly() {
        SavingsGoal existing = goal(7, owner, "1000", TODAY.plusDays(5), TODAY.minusDays(3));
        when(goalRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(goalRepository.saveAndFlush(existing)).thenReturn(existing);
        when(progressCalculator.calculate(existing)).thenReturn(PROGRESS);

        GoalResponse response = service.update(USER_ID, 7L,
                new UpdateGoalRequest(new BigDecimal("2000.5"), TODAY.plusMonths(2)));

        assertThat(response.targetAmount()).hasToString("2000.50");
        assertThat(response.targetDate()).isEqualTo(TODAY.plusMonths(2));
        assertThat(response.startDate()).isEqualTo(TODAY.minusDays(3));
    }

    @Test
    void updateAmountOnlyKeepsExistingTargetDate() {
        SavingsGoal existing = goal(7, owner, "1000", TODAY.plusDays(5), TODAY);
        when(goalRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(goalRepository.saveAndFlush(existing)).thenReturn(existing);
        when(progressCalculator.calculate(existing)).thenReturn(PROGRESS);

        assertThat(service.update(USER_ID, 7L, new UpdateGoalRequest(BigDecimal.TEN, null)).targetDate())
                .isEqualTo(TODAY.plusDays(5));
    }

    @Test
    void updateValidatesBeforeLoading() {
        assertThatThrownBy(() -> service.update(USER_ID, 7L, new UpdateGoalRequest(null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.update(USER_ID, 7L, new UpdateGoalRequest(null, TODAY.minusDays(1))))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(goalRepository);
    }

    @Test
    void deleteRemovesOwnedGoal() {
        SavingsGoal existing = goal(7, owner, "1000", TODAY.plusDays(5), TODAY);
        when(goalRepository.findById(7L)).thenReturn(Optional.of(existing));

        service.delete(USER_ID, 7L);

        ArgumentCaptor<SavingsGoal> deleted = ArgumentCaptor.forClass(SavingsGoal.class);
        verify(goalRepository).delete(deleted.capture());
        assertThat(deleted.getValue()).isSameAs(existing);
    }
}
