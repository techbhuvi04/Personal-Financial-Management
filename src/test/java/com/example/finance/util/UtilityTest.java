package com.example.finance.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finance.dto.auth.LoginRequest;
import com.example.finance.dto.auth.RegisterRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UtilityTest {

    @ParameterizedTest
    @CsvSource({"1, 1.00", "1.005, 1.01", "1.004, 1.00", "-2.5, -2.50"})
    void normalizesMonetaryScaleHalfUp(String input, String expected) {
        assertThat(MonetaryAmounts.normalize(new BigDecimal(input))).hasToString(expected);
    }

    @Test
    void nullAmountNormalizesToZero() {
        assertThat(MonetaryAmounts.normalize(null)).isEqualTo(MonetaryAmounts.ZERO).hasToString("0.00");
    }

    @Test
    void normalizesTextInput() {
        assertThat(InputNormalizer.email("  A@B.Com ")).isEqualTo("a@b.com");
        assertThat(InputNormalizer.phone(" +1 (234) 567-89.0 ")).isEqualTo("+1234567890");
        assertThat(InputNormalizer.trim("  x ")).isEqualTo("x");
        assertThat(InputNormalizer.trimToNull("   ")).isNull();
        assertThat(InputNormalizer.trimToNull(" y ")).isEqualTo("y");
        assertThat(InputNormalizer.email(null)).isNull();
        assertThat(InputNormalizer.phone(null)).isNull();
        assertThat(InputNormalizer.trim(null)).isNull();
        assertThat(InputNormalizer.trimToNull(null)).isNull();
    }

    @Test
    void authRequestsNeverExposePasswordsInToString() {
        assertThat(new RegisterRequest("a@b.com", "secret123", "A", "+1234567890").toString())
                .doesNotContain("secret123");
        assertThat(new LoginRequest("a@b.com", "secret123").toString()).doesNotContain("secret123");
    }
}
