package com.example.finance.config;

import com.example.finance.entity.enums.TransactionType;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC customisations.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Parses {@link TransactionType} query/path parameters case-insensitively, matching how JSON bodies are read
     * ({@code spring.jackson.mapper.accept-case-insensitive-enums}). Unknown values still fail with HTTP 400.
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TransactionType.class,
                value -> TransactionType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    }
}
