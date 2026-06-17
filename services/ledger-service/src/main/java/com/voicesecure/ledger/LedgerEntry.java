package com.voicesecure.ledger;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
        UUID id,
        UUID accountId,
        long signedAmount,
        String currency,
        UUID sagaId,
        EntryType entryType,
        UUID idempotencyKey,
        Instant createdAt
) {
    public LedgerEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(createdAt, "createdAt");
        if (signedAmount == 0) {
            throw new LedgerException("signed_amount cannot be zero");
        }
        if (signedAmount < 0 && entryType != EntryType.DEBIT && entryType != EntryType.REPAIR_DEBIT) {
            throw new LedgerException("negative signed_amount must use a debit entry type");
        }
        if (signedAmount > 0 && entryType != EntryType.CREDIT && entryType != EntryType.REPAIR_CREDIT) {
            throw new LedgerException("positive signed_amount must use a credit entry type");
        }
    }
}

