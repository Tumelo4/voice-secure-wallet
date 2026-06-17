package com.voicesecure.ledger;

import java.util.Objects;
import java.util.UUID;

public record Posting(UUID accountId, long signedAmount, EntryType entryType) {
    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(entryType, "entryType");
        if (signedAmount == 0) {
            throw new LedgerException("posting signed_amount cannot be zero");
        }
    }

    public static Posting debit(UUID accountId, long amount) {
        if (amount <= 0) {
            throw new LedgerException("debit amount must be positive");
        }
        return new Posting(accountId, -amount, EntryType.DEBIT);
    }

    public static Posting credit(UUID accountId, long amount) {
        if (amount <= 0) {
            throw new LedgerException("credit amount must be positive");
        }
        return new Posting(accountId, amount, EntryType.CREDIT);
    }

    public static Posting repairDebit(UUID accountId, long amount) {
        if (amount <= 0) {
            throw new LedgerException("repair debit amount must be positive");
        }
        return new Posting(accountId, -amount, EntryType.REPAIR_DEBIT);
    }

    public static Posting repairCredit(UUID accountId, long amount) {
        if (amount <= 0) {
            throw new LedgerException("repair credit amount must be positive");
        }
        return new Posting(accountId, amount, EntryType.REPAIR_CREDIT);
    }
}

