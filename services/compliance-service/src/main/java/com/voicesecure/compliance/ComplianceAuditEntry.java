package com.voicesecure.compliance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ComplianceAuditEntry(
        UUID auditId,
        UUID userId,
        ComplianceHitType hitType,
        String subject,
        String reason,
        Instant screenedAt
) {
    public ComplianceAuditEntry {
        Objects.requireNonNull(auditId, "auditId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(hitType, "hitType");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(screenedAt, "screenedAt");
    }
}

