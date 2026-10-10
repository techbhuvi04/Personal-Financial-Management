package com.example.finance.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary scale and rounding policy shared by every calculation and response: two decimal places, HALF_UP.
 */
public final class MonetaryAmounts {

    /** Number of decimal places for amounts and percentages. */
    public static final int SCALE = 2;

    /** Rounding mode for amounts and percentages. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Zero at the monetary scale. */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private MonetaryAmounts() {
    }

    /**
     * @param value any amount, may be null
     * @return the value at the monetary scale, or {@link #ZERO} for null
     */
    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }
}
