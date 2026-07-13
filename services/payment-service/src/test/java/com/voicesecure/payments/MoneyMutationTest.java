package com.voicesecure.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

final class MoneyMutationTest {
    @Test
    void convertsExactDecimalValuesToMinorUnits() {
        assertEquals(25_000L, Money.parse("250.00", "zar").minorUnits());
        assertEquals(250L, new Money(new BigDecimal("250"), Currency.getInstance("JPY")).minorUnits());
    }

    @Test
    void rejectsNonPositiveInvalidScaleAndInvalidCurrency() {
        assertMessage("positive", () -> Money.parse("0.00", "ZAR"));
        assertMessage("positive", () -> Money.parse("-1.00", "ZAR"));
        assertMessage("scale", () -> Money.parse("1.001", "ZAR"));
        assertMessage("invalid monetary amount", () -> Money.parse("1.50", "NOT"));
    }

    @Test
    void neverRoundsAuthoritativeMoney() {
        assertThrows(ArithmeticException.class,
                () -> new Money(new BigDecimal("92233720368547758.08"), Currency.getInstance("ZAR")).minorUnits());
    }

    private static void assertMessage(String expected, Runnable action) {
        PaymentException exception = assertThrows(PaymentException.class, action::run);
        assertTrue(exception.getMessage().contains(expected), exception.getMessage());
    }
}
