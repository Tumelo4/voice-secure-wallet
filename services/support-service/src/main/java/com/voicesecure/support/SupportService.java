package com.voicesecure.support;

import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.LedgerEntry;
import com.voicesecure.ledger.LedgerService;
import com.voicesecure.ledger.RepairRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SupportService {
    private final SupportRepository repository;
    private final LedgerService ledgerService;

    public SupportService(SupportRepository repository, LedgerService ledgerService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService");
    }

    public void ingestLedgerBatch(LedgerBatch batch) {
        repository.ingestLedgerBatch(batch);
    }

    public List<SupportTransactionView> searchTransactions(UUID accountId, String currency) {
        return repository.searchTransactions(accountId, currency);
    }

    public SupportCase freezeAccount(UUID accountId, String reason, String openedBy) {
        SupportCase supportCase = SupportCase.freeze(UUID.randomUUID(), accountId, reason, openedBy, Instant.now());
        repository.saveCase(supportCase);
        repository.appendAudit(new SupportAuditEntry(
                UUID.randomUUID(),
                supportCase.caseId(),
                "support.account_frozen",
                openedBy,
                "account=" + accountId + ", reason=" + reason,
                supportCase.updatedAt()
        ));
        return supportCase;
    }

    public SupportCase openDispute(UUID accountId, UUID transactionId, String reason, String openedBy) {
        SupportCase supportCase = SupportCase.dispute(UUID.randomUUID(), accountId, transactionId, reason, openedBy, Instant.now());
        repository.saveCase(supportCase);
        repository.appendAudit(new SupportAuditEntry(
                UUID.randomUUID(),
                supportCase.caseId(),
                "support.dispute_opened",
                openedBy,
                "account=" + accountId + ", transaction=" + transactionId + ", reason=" + reason,
                supportCase.updatedAt()
        ));
        return supportCase;
    }

    public LedgerBatch requestRepair(RepairRequest repairRequest) {
        LedgerBatch batch = ledgerService.repair(repairRequest);
        repository.ingestLedgerBatch(batch);
        SupportCase supportCase = SupportCase.repairEscalation(
                UUID.randomUUID(),
                repairRequest.repairId(),
                repairRequest.sagaId(),
                repairRequest.justification(),
                repairRequest.requestedBy(),
                Instant.now()
        );
        repository.saveCase(supportCase);
        repository.appendAudit(new SupportAuditEntry(
                UUID.randomUUID(),
                supportCase.caseId(),
                "support.repair_linked",
                repairRequest.requestedBy(),
                "repair=" + repairRequest.repairId() + ", saga=" + repairRequest.sagaId(),
                supportCase.updatedAt()
        ));
        return batch;
    }

    public static SupportTransactionView toView(LedgerEntry entry) {
        return new SupportTransactionView(
                entry.id(),
                entry.sagaId(),
                entry.accountId(),
                entry.signedAmount(),
                entry.currency(),
                entry.entryType().name(),
                entry.createdAt()
        );
    }
}
