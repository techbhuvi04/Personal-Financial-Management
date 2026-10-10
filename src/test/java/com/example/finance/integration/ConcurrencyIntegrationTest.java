package com.example.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finance.dto.auth.RegisterRequest;
import com.example.finance.dto.category.CreateCategoryRequest;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.exception.DuplicateResourceException;
import com.example.finance.service.AuthService;
import com.example.finance.service.CategoryService;
import com.example.finance.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Races identical create requests; exactly one must win and the rest must surface as 409-mapped duplicates,
 * never as unhandled constraint violations.
 */
class ConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final int THREADS = 6;

    @Autowired
    private AuthService authService;

    @Autowired
    private CategoryService categoryService;

    @RepeatedTest(3)
    void concurrentRegistrationsWithSameEmailCreateExactlyOneUser() throws Exception {
        String email = uniqueEmail();
        List<Throwable> failures = race(() -> authService.register(
                new RegisterRequest(email, PASSWORD, "Racer", "+1234567890")));

        assertThat(failures).hasSize(THREADS - 1).allMatch(DuplicateResourceException.class::isInstance);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @RepeatedTest(3)
    void concurrentCategoryCreationWithSameNameCreatesExactlyOne() throws Exception {
        String email = uniqueEmail();
        Long userId = authService.register(new RegisterRequest(email, PASSWORD, "Racer", "+1234567890"));
        List<Throwable> failures = race(() -> categoryService.createCategory(userId,
                new CreateCategoryRequest("Side Hustle", TransactionType.INCOME)));

        assertThat(failures).hasSize(THREADS - 1).allMatch(DuplicateResourceException.class::isInstance);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE owner_id = ?",
                Integer.class, userId)).isEqualTo(1);
    }

    private static List<Throwable> race(Callable<?> task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                failures.add(ex.getCause());
            } catch (java.util.concurrent.TimeoutException ex) {
                failures.add(ex);
            }
        }
        executor.shutdownNow();
        return failures;
    }
}
