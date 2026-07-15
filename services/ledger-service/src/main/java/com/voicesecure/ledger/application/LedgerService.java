package com.voicesecure.ledger.application;

import com.voicesecure.ledger.*;
import com.voicesecure.ledger.domain.LedgerRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Duration;

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

    public FundReservation reserveFunds(
            UUID reservationId, UUID paymentId, UUID accountId, long amount, String currency, Duration ttl
    ) {
        return repository.reserve(reservationId, paymentId, accountId, amount, currency, ttl);
    }

    public FundReservation releaseFunds(UUID reservationId) {
        return repository.release(reservationId);
    }

    public ReconciliationReport reconcile() {
        return ReconciliationReport.from(repository.entries());
    }
}
