package com.voicesecure.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() <= 0) {
            throw new PaymentException("amount must be positive");
        }
        int scale = currency.getDefaultFractionDigits();
        if (scale < 0 || amount.scale() > scale) {
            throw new PaymentException("invalid amount scale for currency");
        }
    }

    public static Money parse(String value, String currencyCode) {
        try {
            Currency currency = Currency.getInstance(Objects.requireNonNull(currencyCode, "currencyCode").trim().toUpperCase(java.util.Locale.ROOT));
            return new Money(new BigDecimal(Objects.requireNonNull(value, "value").trim()), currency);
        } catch (RuntimeException exception) {
            if (exception instanceof PaymentException paymentException) {
                throw paymentException;
            }
            throw new PaymentException("invalid monetary amount");
        }
    }

    public long minorUnits() {
        return amount.movePointRight(currency.getDefaultFractionDigits())
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }
}
