package com.voicesecure.support;

import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.LedgerEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemorySupportRepository implements SupportRepository {
    private final Map<UUID, SupportTransactionView> transactionsByEntryId = new LinkedHashMap<>();
    private final Map<UUID, SupportCase> casesById = new LinkedHashMap<>();
    private final List<SupportAuditEntry> auditLog = new ArrayList<>();

    @Override
    public synchronized void ingestLedgerBatch(LedgerBatch batch) {
        for (LedgerEntry entry : batch.entries()) {
            transactionsByEntryId.putIfAbsent(entry.id(), SupportService.toView(entry));
        }
    }

    @Override
    public synchronized List<SupportTransactionView> searchTransactions(UUID accountId, String currency) {
        return transactionsByEntryId.values().stream()
                .filter(view -> view.accountId().equals(accountId))
                .filter(view -> currency == null || currency.isBlank() || view.currency().equals(currency))
                .toList();
    }

    @Override
    public synchronized void saveCase(SupportCase supportCase) {
        casesById.put(supportCase.caseId(), supportCase);
    }

    @Override
    public synchronized Optional<SupportCase> findCase(UUID caseId) {
        return Optional.ofNullable(casesById.get(caseId));
    }

    @Override
    public synchronized void appendAudit(SupportAuditEntry auditEntry) {
        auditLog.add(auditEntry);
    }

    @Override
    public synchronized List<SupportAuditEntry> auditLog() {
        return List.copyOf(auditLog);
    }

    @Override
    public synchronized List<SupportCase> cases() {
        return List.copyOf(casesById.values());
    }
}
