package com.voicesecure.compliance;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class ComplianceService {
    private final ComplianceRepository repository;

    public ComplianceService(ComplianceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void addPep(String nationalId, String description) {
        repository.addPep(normalize(nationalId), description);
    }

    public void addSanctionsMatch(String fullName, String description) {
        repository.addSanctionsMatch(normalize(fullName), description);
    }

    public void setAmlThreshold(long threshold) {
        repository.setAmlThreshold(threshold);
    }

    public ComplianceScreeningResult screen(ComplianceProfile profile) {
        String normalizedId = normalize(profile.nationalId());
        String normalizedName = normalize(profile.fullName());
        ComplianceHitType hitType = ComplianceHitType.NONE;
        String reason = "clear";

        if (repository.pepIds().contains(normalizedId)) {
            hitType = ComplianceHitType.PEP_MATCH;
            reason = "pep match on national id";
        } else if (repository.sanctionsNames().contains(normalizedName)) {
            hitType = ComplianceHitType.SANCTIONS_MATCH;
            reason = "sanctions match on name";
        } else if (profile.transactionAmount() >= repository.amlThreshold()) {
            hitType = ComplianceHitType.AML_FLAG;
            reason = "aml threshold exceeded";
        }

        ComplianceScreeningResult result = new ComplianceScreeningResult(
                UUID.randomUUID(),
                profile.userId(),
                hitType,
                hitType != ComplianceHitType.NONE,
                normalizedName,
                reason,
                Instant.now()
        );
        repository.appendAudit(new ComplianceAuditEntry(
                UUID.randomUUID(),
                profile.userId(),
                hitType,
                normalizedName,
                reason,
                result.screenedAt()
        ));
        return result;
    }

    public List<ComplianceAuditEntry> auditTrail() {
        return repository.auditTrail();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
