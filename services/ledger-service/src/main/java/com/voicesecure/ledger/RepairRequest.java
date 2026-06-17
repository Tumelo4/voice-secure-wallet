package com.voicesecure.ledger;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RepairRequest(
        UUID repairId,
        UUID sagaId,
        UUID idempotencyKey,
        String currency,
        List<Posting> postings,
        String justification,
        String requestedBy
) {
    public RepairRequest {
        Objects.requireNonNull(repairId, "repairId");
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(justification, "justification");
        Objects.requireNonNull(requestedBy, "requestedBy");
        postings = List.copyOf(postings);
        if (justification.trim().length() < 12) {
            throw new LedgerException("repair justification must be at least 12 characters");
        }
        if (requestedBy.trim().isEmpty()) {
            throw new LedgerException("repair requester is required");
        }
        long sum = postings.stream().mapToLong(Posting::signedAmount).sum();
        if (sum != 0) {
            throw new LedgerException("repair postings must balance to zero");
        }
    }
}

