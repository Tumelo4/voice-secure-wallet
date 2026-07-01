package com.voicesecure.wallet;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WalletAccount(
        UUID userId,
        UUID accountId,
        String displayName,
        String currency,
        Instant openedAt
) {
    public WalletAccount {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(openedAt, "openedAt");
        displayName = displayName.trim();
        currency = currency.trim().toUpperCase(java.util.Locale.ROOT);
        if (displayName.isBlank()) {
            throw new WalletException("wallet display name is required");
        }
        if (currency.isBlank()) {
            throw new WalletException("wallet currency is required");
        }
    }
}
