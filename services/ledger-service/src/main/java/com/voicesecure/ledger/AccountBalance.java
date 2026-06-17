package com.voicesecure.ledger;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccountBalance(
        UUID accountId,
        long balance,
        String currency,
        long version,
        Instant updatedAt
) {
    public AccountBalance {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (balance < 0) {
            throw new LedgerException("account balance cannot be negative");
        }
        if (version < 0) {
            throw new LedgerException("account balance version cannot be negative");
        }
    }
}

