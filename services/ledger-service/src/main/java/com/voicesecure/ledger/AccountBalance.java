package com.voicesecure.ledger;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccountBalance(
        UUID accountId,
        long balance,
        long availableBalance,
        long reservedBalance,
        long pendingBalance,
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
        if (availableBalance < 0 || reservedBalance < 0 || pendingBalance < 0) {
            throw new LedgerException("account balance components cannot be negative");
        }
        if (availableBalance + reservedBalance > balance) {
            throw new LedgerException("available and reserved balances cannot exceed current balance");
        }
        if (version < 0) {
            throw new LedgerException("account balance version cannot be negative");
        }
    }

    public AccountBalance(UUID accountId, long balance, String currency, long version, Instant updatedAt) {
        this(accountId, balance, balance, 0, 0, currency, version, updatedAt);
    }
}
