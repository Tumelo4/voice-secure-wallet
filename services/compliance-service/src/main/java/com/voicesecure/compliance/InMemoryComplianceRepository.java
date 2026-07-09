package com.voicesecure.compliance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InMemoryComplianceRepository implements ComplianceRepository {
    private final Set<String> pepIds = new LinkedHashSet<>();
    private final Set<String> sanctionsNames = new LinkedHashSet<>();
    private final List<ComplianceAuditEntry> audits = new ArrayList<>();
    private long amlThreshold = 50000L;

    @Override
    public synchronized void addPep(String nationalId, String description) {
        pepIds.add(normalize(nationalId));
    }

    @Override
    public synchronized void addSanctionsMatch(String fullName, String description) {
        sanctionsNames.add(normalize(fullName));
    }

    @Override
    public synchronized void setAmlThreshold(long threshold) {
        this.amlThreshold = threshold;
    }

    @Override
    public synchronized long amlThreshold() {
        return amlThreshold;
    }

    @Override
    public synchronized Set<String> pepIds() {
        return Set.copyOf(pepIds);
    }

    @Override
    public synchronized Set<String> sanctionsNames() {
        return Set.copyOf(sanctionsNames);
    }

    @Override
    public synchronized List<ComplianceAuditEntry> auditTrail() {
        return List.copyOf(audits);
    }

    @Override
    public synchronized void appendAudit(ComplianceAuditEntry auditEntry) {
        audits.add(auditEntry);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

