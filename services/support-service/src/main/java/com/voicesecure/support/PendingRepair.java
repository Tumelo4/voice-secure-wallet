package com.voicesecure.support;

import com.voicesecure.ledger.Posting;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PendingRepair(
        UUID repairId, UUID caseId, UUID sagaId, String currency, List<Posting> postings,
        String justification, String requestedBy, Status status, String approvedBy, Instant createdAt
) {
    public enum Status { PENDING_APPROVAL, APPLIED, REJECTED }
    public PendingRepair {
        Objects.requireNonNull(repairId); Objects.requireNonNull(caseId); Objects.requireNonNull(sagaId);
        currency = Objects.requireNonNull(currency).trim().toUpperCase(java.util.Locale.ROOT);
        postings = List.copyOf(Objects.requireNonNull(postings));
        justification = Objects.requireNonNull(justification).trim();
        requestedBy = Objects.requireNonNull(requestedBy).trim();
        Objects.requireNonNull(status); approvedBy = approvedBy == null ? "" : approvedBy.trim(); Objects.requireNonNull(createdAt);
        if (justification.length() < 12) throw new SupportException("repair justification must be at least 12 characters");
        if (requestedBy.isEmpty()) throw new SupportException("repair requester is required");
    }
}
