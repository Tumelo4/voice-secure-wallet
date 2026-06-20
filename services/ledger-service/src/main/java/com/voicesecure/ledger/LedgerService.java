package com.voicesecure.ledger;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LedgerService {
    private final LedgerRepository repository;

    public LedgerService(LedgerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void createAccount(UUID accountId, String currency, long openingBalance) {
        repository.createAccount(accountId, currency, openingBalance);
    }

    public LedgerBatch transfer(
            UUID sagaId,
            UUID idempotencyKey,
            UUID fromAccountId,
            UUID toAccountId,
            long amount,
            String currency
    ) {
        if (amount <= 0) {
            throw new LedgerException("transfer amount must be positive");
        }
        LedgerTransaction transaction = new LedgerTransaction(
                sagaId,
                idempotencyKey,
                currency,
                List.of(
                        Posting.debit(fromAccountId, amount),
                        Posting.credit(toAccountId, amount)
                )
        );
        return repository.append(transaction);
    }

    public LedgerBatch repair(RepairRequest repairRequest) {
        return repository.appendRepair(repairRequest);
    }

    public ReconciliationReport reconcile() {
        return ReconciliationReport.from(repository.entries());
    }
}
