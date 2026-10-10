package com.example.finance.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Pins the business clock so date rules (future dates, goal progress windows) are deterministic.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    /** The fixed "today" used by all integration tests. */
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Bean
    @Primary
    Clock fixedTestClock() {
        return Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
    }
}
