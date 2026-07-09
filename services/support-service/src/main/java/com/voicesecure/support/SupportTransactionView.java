package com.voicesecure.support;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SupportTransactionView(
        UUID entryId,
        UUID transactionId,
        UUID accountId,
        long signedAmount,
        String currency,
        String entryType,
        Instant postedAt
) {
    public SupportTransactionView {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(postedAt, "postedAt");
        if (currency.isBlank()) {
            throw new SupportException("transaction currency cannot be blank");
        }
        if (entryType.isBlank()) {
            throw new SupportException("transaction entry type cannot be blank");
        }
    }
}
