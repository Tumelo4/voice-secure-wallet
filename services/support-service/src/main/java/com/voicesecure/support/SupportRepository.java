package com.voicesecure.support;

import com.voicesecure.ledger.LedgerBatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportRepository {
    void ingestLedgerBatch(LedgerBatch batch);

    List<SupportTransactionView> searchTransactions(UUID accountId, String currency);

    void saveCase(SupportCase supportCase);

    Optional<SupportCase> findCase(UUID caseId);

    void appendAudit(SupportAuditEntry auditEntry);

    List<SupportAuditEntry> auditLog();

    List<SupportCase> cases();
}
