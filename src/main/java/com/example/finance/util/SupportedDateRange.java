package com.example.finance.util;

import java.time.LocalDate;

/**
 * Calendar range accepted for every business date in the API (transaction dates, goal dates, list filters,
 * report periods). Keeping one range means anything that can be stored can also be reported, and values outside
 * what the database can store are rejected as client errors instead of failing in SQL.
 */
public final class SupportedDateRange {

    /** Earliest supported date. */
    public static final LocalDate MIN = LocalDate.of(1900, 1, 1);

    /** Latest supported date. */
    public static final LocalDate MAX = LocalDate.of(9999, 12, 31);

    private SupportedDateRange() {
    }

    /**
     * @param date date to check, not null
     * @return whether the date lies within {@link #MIN}..{@link #MAX}
     */
    public static boolean contains(LocalDate date) {
        return !date.isBefore(MIN) && !date.isAfter(MAX);
    }

    /**
     * @return human-readable bounds for error messages
     */
    public static String describe() {
        return "between " + MIN + " and " + MAX;
    }
}
