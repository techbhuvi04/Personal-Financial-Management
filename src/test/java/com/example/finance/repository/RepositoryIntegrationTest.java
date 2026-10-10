package com.example.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.finance.dto.transaction.TransactionFilter;
import com.example.finance.entity.Category;
import com.example.finance.entity.SavingsGoal;
import com.example.finance.entity.Transaction;
import com.example.finance.entity.User;
import com.example.finance.entity.enums.TransactionType;
import com.example.finance.repository.projection.CategoryTotal;
import com.example.finance.repository.projection.TypeTotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs against the Flyway-migrated schema to verify mappings, constraints and aggregation queries.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private SavingsGoalRepository goalRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User alice;
    private User bob;
    private Category salary;
    private Category food;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("alice@example.com", "hash", "Alice", "+1234567890"));
        bob = userRepository.save(new User("bob@example.com", "hash", "Bob", "+1234567890"));
        salary = categoryRepository.findAccessibleByNormalizedName(alice.getId(), "salary").orElseThrow();
        food = categoryRepository.findAccessibleByNormalizedName(alice.getId(), "food").orElseThrow();
    }

    @Test
    void flywaySeedsSevenDefaultCategories() {
        assertThat(categoryRepository.findAllAccessible(alice.getId()))
                .extracting(Category::getName)
                .containsExactly("Salary", "Food", "Rent", "Transportation", "Entertainment", "Healthcare", "Utilities");
        assertThat(salary.isDefaultCategory()).isTrue();
        assertThat(salary.getOwner()).isNull();
        assertThat(categoryRepository.existsByDefaultCategoryTrueAndNormalizedName("utilities")).isTrue();
    }

    @Test
    void customCategoriesAreScopedToOwner() {
        categoryRepository.saveAndFlush(Category.custom("Gifts", TransactionType.EXPENSE, alice));

        assertThat(categoryRepository.findAllAccessible(alice.getId())).hasSize(8);
        assertThat(categoryRepository.findAllAccessible(bob.getId())).hasSize(7);
        assertThat(categoryRepository.findAccessibleByNormalizedName(bob.getId(), "gifts")).isEmpty();
        assertThat(categoryRepository.existsByOwnerIdAndNormalizedName(alice.getId(), "gifts")).isTrue();
    }

    @Test
    void uniqueConstraintsAreEnforcedByDatabase() {
        categoryRepository.saveAndFlush(Category.custom("Gifts", TransactionType.EXPENSE, alice));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(
                Category.custom("GIFTS", TransactionType.INCOME, alice)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateUsernameIsRejectedByDatabase() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("alice@example.com", "hash", "Other", "+1234567890")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonPositiveAmountIsRejectedByCheckConstraint() {
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(
                new Transaction(alice, food, BigDecimal.ZERO, LocalDate.of(2024, 1, 1), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referencedCategoryCannotBeDeleted() {
        Category gifts = categoryRepository.saveAndFlush(Category.custom("Gifts", TransactionType.EXPENSE, alice));
        transactionRepository.saveAndFlush(new Transaction(alice, gifts, BigDecimal.TEN, LocalDate.of(2024, 1, 1), null));

        assertThat(transactionRepository.existsByCategoryId(gifts.getId())).isTrue();
        assertThatThrownBy(() -> {
            categoryRepository.delete(gifts);
            categoryRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void goalDatesMustBeOrderedInDatabase() {
        assertThatThrownBy(() -> goalRepository.saveAndFlush(new SavingsGoal(alice, "Bad", BigDecimal.TEN,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findOwnedByIdOnlyReturnsOwnersTransaction() {
        Transaction tx = transactionRepository.saveAndFlush(
                new Transaction(alice, food, BigDecimal.TEN, LocalDate.of(2024, 1, 1), null));

        assertThat(transactionRepository.findOwnedById(tx.getId(), alice.getId())).isPresent();
        assertThat(transactionRepository.findOwnedById(tx.getId(), bob.getId())).isEmpty();
    }

    @Test
    void aggregationQueriesRespectOwnerAndDateBounds() {
        save(alice, salary, "3000", "2024-01-01");
        save(alice, salary, "1000", "2024-01-31");
        save(alice, food, "150.25", "2024-01-15");
        save(alice, food, "100", "2024-02-01");
        save(bob, salary, "9999", "2024-01-10");

        List<CategoryTotal> january = transactionRepository.sumByCategory(alice.getId(),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1));
        assertThat(january).extracting(CategoryTotal::categoryName).containsExactly("Food", "Salary");
        assertThat(january.get(0).total()).isEqualByComparingTo("150.25");
        assertThat(january.get(1).total()).isEqualByComparingTo("4000");

        List<TypeTotal> byType = transactionRepository.sumByType(alice.getId(),
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 2, 1));
        assertThat(byType).anySatisfy(total -> {
            assertThat(total.type()).isEqualTo(TransactionType.INCOME);
            assertThat(total.total()).isEqualByComparingTo("1000");
        }).anySatisfy(total -> {
            assertThat(total.type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(total.total()).isEqualByComparingTo("250.25");
        });
    }

    @Test
    void specificationCombinesFiltersForOwner() {
        save(alice, salary, "1", "2024-01-01");
        save(alice, food, "2", "2024-01-05");
        save(alice, food, "3", "2024-01-10");
        save(bob, food, "4", "2024-01-05");

        List<Transaction> result = transactionRepository.findAll(
                TransactionSpecifications.ownedByAndMatching(alice.getId(),
                        new TransactionFilter(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 9), food.getId(),
                                "Food", TransactionType.EXPENSE)),
                Sort.by(Sort.Order.desc("date")));

        assertThat(result).extracting(Transaction::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2.00"));
        assertThat(transactionRepository.count(TransactionSpecifications.ownedByAndMatching(alice.getId(),
                new TransactionFilter(null, null, null, null, null)))).isEqualTo(3);
    }

    @Test
    void goalsAreListedPerUserInCreationOrder() {
        goalRepository.saveAndFlush(new SavingsGoal(alice, "A", BigDecimal.TEN, LocalDate.of(2030, 1, 1),
                LocalDate.of(2024, 1, 1)));
        goalRepository.saveAndFlush(new SavingsGoal(alice, "B", BigDecimal.TEN, LocalDate.of(2030, 1, 1),
                LocalDate.of(2024, 1, 1)));
        goalRepository.saveAndFlush(new SavingsGoal(bob, "C", BigDecimal.TEN, LocalDate.of(2030, 1, 1),
                LocalDate.of(2024, 1, 1)));

        assertThat(goalRepository.findByUserIdOrderByIdAsc(alice.getId()))
                .extracting(SavingsGoal::getGoalName).containsExactly("A", "B");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM savings_goals", Integer.class)).isEqualTo(3);
    }

    private void save(User owner, Category category, String amount, String date) {
        transactionRepository.saveAndFlush(new Transaction(owner, category, new BigDecimal(amount),
                LocalDate.parse(date), null));
    }
}
