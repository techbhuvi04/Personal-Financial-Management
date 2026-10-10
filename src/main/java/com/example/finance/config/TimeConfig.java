package com.example.finance.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the business clock. Injecting a {@link Clock} instead of calling {@code LocalDate.now()} keeps
 * date rules deterministic in tests.
 */
@Configuration
public class TimeConfig {

    /**
     * @param properties application properties holding the business time zone
     * @return the system clock in the configured zone
     */
    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(properties.timeZone());
    }
}
