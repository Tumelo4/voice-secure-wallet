package com.voicesecure.payments;

import java.math.BigDecimal;
import java.util.Currency;

public final class MoneyTests {
    public static void main(String[] args) {
        assertEquals(25_000L, Money.parse("250.00", "ZAR").minorUnits(), "decimal parsing");
        assertEquals(250L, new Money(new BigDecimal("250"), Currency.getInstance("JPY")).minorUnits(), "zero-decimal currency");
        expectFailure(() -> Money.parse("1.001", "ZAR"), "scale");
        expectFailure(() -> Money.parse("1.5", "NOT"), "invalid monetary amount");
        expectFailure(() -> Money.parse("0.00", "ZAR"), "positive");
        System.out.println("Money tests passed: 5");
    }

    private static void expectFailure(Runnable action, String expected) {
        try { action.run(); } catch (PaymentException exception) {
            if (exception.getMessage().contains(expected)) return;
            throw new AssertionError("wrong error: " + exception.getMessage());
        }
        throw new AssertionError("expected failure");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
}
