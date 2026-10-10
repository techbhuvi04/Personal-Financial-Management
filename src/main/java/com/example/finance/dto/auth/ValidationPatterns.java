package com.example.finance.dto.auth;

/**
 * Regular expressions for registration input.
 */
final class ValidationPatterns {

    /** Pragmatic email check: local part, '@', domain with at least one dot and an alphabetic TLD. */
    static final String EMAIL = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$";

    /** At least one letter, at least one digit, no whitespace. Length is checked separately. */
    static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d)\\S+$";

    /** Unicode letters plus common name punctuation; must start with a letter. */
    static final String FULL_NAME = "^\\p{L}[\\p{L} .'-]*$";

    /** Optional '+', then 7-15 digits. Leading zeros are allowed (e.g. "+0987654321" in the assignment's tests). */
    static final String PHONE = "^\\+?\\d{7,15}$";

    private ValidationPatterns() {
    }
}
