package com.voicesecure.support;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SupportAuditEntry(
        UUID auditId,
        UUID caseId,
        String action,
        String actor,
        String details,
        Instant occurredAt
) {
    public SupportAuditEntry {
        Objects.requireNonNull(auditId, "auditId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (action.isBlank()) {
            throw new SupportException("audit action cannot be blank");
        }
        if (actor.isBlank()) {
            throw new SupportException("audit actor cannot be blank");
        }
    }
}
