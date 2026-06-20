package com.voicesecure.compliance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ComplianceScreeningResult(
        UUID caseId,
        UUID userId,
        ComplianceHitType hitType,
        boolean hit,
        String subject,
        String reason,
        Instant screenedAt
) {
    public ComplianceScreeningResult {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(hitType, "hitType");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(screenedAt, "screenedAt");
    }
}
