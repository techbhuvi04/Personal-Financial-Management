package com.example.finance.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Removes all user-owned rows while keeping the Flyway-seeded default categories.
 */
public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM savings_goals");
        jdbcTemplate.update("DELETE FROM categories WHERE is_default = FALSE");
        jdbcTemplate.update("DELETE FROM users");
    }
}
