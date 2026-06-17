package com.voicesecure.compliance;

import java.util.List;
import java.util.Set;

public interface ComplianceRepository {
    void addPep(String nationalId, String description);

    void addSanctionsMatch(String fullName, String description);

    void setAmlThreshold(long threshold);

    long amlThreshold();

    Set<String> pepIds();

    Set<String> sanctionsNames();

    List<ComplianceAuditEntry> auditTrail();

    void appendAudit(ComplianceAuditEntry auditEntry);
}

