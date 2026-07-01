package com.voicesecure.wallet;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WalletBalance(
        UUID accountId,
        String currency,
        long balance,
        long version,
        Instant updatedAt
) {
    public WalletBalance {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(updatedAt, "updatedAt");
        currency = currency.trim().toUpperCase(java.util.Locale.ROOT);
        if (currency.isBlank()) {
            throw new WalletException("wallet balance currency is required");
        }
        if (balance < 0) {
            throw new WalletException("wallet balance cannot be negative");
        }
        if (version < 0) {
            throw new WalletException("wallet balance version cannot be negative");
        }
    }
}
