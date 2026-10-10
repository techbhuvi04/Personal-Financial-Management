package com.example.finance.util;

import java.util.Locale;

/**
 * Canonicalises free-text input at the API boundary so validation and uniqueness checks see the same value
 * that will be stored.
 */
public final class InputNormalizer {

    private InputNormalizer() {
    }

    /**
     * @param value raw text, may be null
     * @return the trimmed text, or null
     */
    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * @param value raw email, may be null
     * @return the trimmed, lower-cased email, or null
     */
    public static String email(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Removes common visual separators (spaces, dashes, dots, parentheses) so "+1 (234) 567-890" and
     * "+1234567890" are stored identically.
     *
     * @param value raw phone number, may be null
     * @return the compacted phone number, or null
     */
    public static String phone(String value) {
        return value == null ? null : value.trim().replaceAll("[\\s().-]", "");
    }

    /**
     * @param value raw optional text, may be null
     * @return the trimmed text, or null when absent or blank
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
