package com.voicesecure.support;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SupportCase(
        UUID caseId,
        SupportCaseType type,
        SupportCaseStatus status,
        UUID subjectId,
        UUID transactionId,
        UUID linkedRepairId,
        String reason,
        String openedBy,
        Instant openedAt,
        Instant updatedAt
) {
    public SupportCase {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (reason.isBlank()) {
            throw new SupportException("support case reason cannot be blank");
        }
        if (openedBy.isBlank()) {
            throw new SupportException("support case opener cannot be blank");
        }
        if (type == SupportCaseType.DISPUTE && transactionId == null) {
            throw new SupportException("dispute cases require a transaction id");
        }
        if (type == SupportCaseType.REPAIR_ESCALATION && linkedRepairId == null) {
            throw new SupportException("repair escalations require a repair id");
        }
    }

    public static SupportCase freeze(UUID caseId, UUID accountId, String reason, String openedBy, Instant now) {
        return new SupportCase(caseId, SupportCaseType.ACCOUNT_FREEZE, SupportCaseStatus.FROZEN, accountId, null, null, reason, openedBy, now, now);
    }

    public static SupportCase dispute(UUID caseId, UUID accountId, UUID transactionId, String reason, String openedBy, Instant now) {
        return new SupportCase(caseId, SupportCaseType.DISPUTE, SupportCaseStatus.DISPUTED, accountId, transactionId, null, reason, openedBy, now, now);
    }

    public static SupportCase repairEscalation(UUID caseId, UUID repairId, UUID transactionId, String reason, String openedBy, Instant now) {
        return new SupportCase(caseId, SupportCaseType.REPAIR_ESCALATION, SupportCaseStatus.ESCALATED, repairId, transactionId, repairId, reason, openedBy, now, now);
    }
}
