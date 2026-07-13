package com.voicesecure.support;

import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.LedgerEntry;
import com.voicesecure.ledger.LedgerService;
import com.voicesecure.ledger.RepairRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.voicesecure.ledger.Posting;

public final class SupportService {
    private final SupportRepository repository;
    private final LedgerRepairPort ledgerRepairPort;

    public SupportService(SupportRepository repository, LedgerService ledgerService) {
        this(repository, new LedgerServiceRepairPort(ledgerService));
    }

    public SupportService(SupportRepository repository, LedgerRepairPort ledgerRepairPort) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ledgerRepairPort = Objects.requireNonNull(ledgerRepairPort, "ledgerRepairPort");
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

    public PendingRepair requestRepair(
            UUID repairId, UUID sagaId, String currency, List<Posting> postings, String justification, String requestedBy
    ) {
        UUID caseId = UUID.randomUUID();
        Instant now = Instant.now();
        PendingRepair pending = new PendingRepair(
                repairId, caseId, sagaId, currency, postings, justification, requestedBy,
                PendingRepair.Status.PENDING_APPROVAL, "", now);
        SupportCase supportCase = SupportCase.repairEscalation(
                caseId, repairId, sagaId, justification, requestedBy, now
        );
        repository.saveCase(supportCase);
        repository.savePendingRepair(pending);
        repository.appendAudit(new SupportAuditEntry(
                UUID.randomUUID(),
                supportCase.caseId(),
                "support.repair_requested",
                requestedBy,
                "repair=" + repairId + ", saga=" + sagaId,
                supportCase.updatedAt()
        ));
        return pending;
    }

    public LedgerBatch approveRepair(UUID repairId, UUID idempotencyKey, String approvedBy) {
        PendingRepair pending = repository.findPendingRepair(repairId)
                .orElseThrow(() -> new SupportException("pending repair not found"));
        String approver = Objects.requireNonNull(approvedBy, "approvedBy").trim();
        if (approver.isEmpty() || approver.equals(pending.requestedBy())) {
            throw new SupportException("repair approver must be different from requester");
        }
        if (pending.status() != PendingRepair.Status.PENDING_APPROVAL) throw new SupportException("repair is not pending approval");
        RepairRequest repairRequest = new RepairRequest(
                pending.repairId(), pending.sagaId(), idempotencyKey, pending.currency(), pending.postings(),
                pending.justification(), pending.requestedBy(), approver);
        LedgerBatch batch = ledgerRepairPort.repair(repairRequest);
        repository.ingestLedgerBatch(batch);
        repository.savePendingRepair(new PendingRepair(
                pending.repairId(), pending.caseId(), pending.sagaId(), pending.currency(), pending.postings(),
                pending.justification(), pending.requestedBy(), PendingRepair.Status.APPLIED, approver, pending.createdAt()));
        repository.appendAudit(new SupportAuditEntry(
                UUID.randomUUID(),
                pending.caseId(),
                "support.repair_linked",
                approver,
                "repair=" + pending.repairId() + ", saga=" + pending.sagaId() + ", requestedBy=" + pending.requestedBy(),
                Instant.now()
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
