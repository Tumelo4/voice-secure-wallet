package com.voicesecure.ledger;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LedgerTransaction(
        UUID sagaId,
        UUID idempotencyKey,
        String currency,
        List<Posting> postings
) {
    public LedgerTransaction {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(currency, "currency");
        postings = List.copyOf(postings);
        if (postings.size() < 2) {
            throw new LedgerException("ledger transaction requires at least two postings");
        }
        long sum = postings.stream().mapToLong(Posting::signedAmount).sum();
        if (sum != 0) {
            throw new LedgerException("ledger transaction must balance to zero");
        }
    }
}
