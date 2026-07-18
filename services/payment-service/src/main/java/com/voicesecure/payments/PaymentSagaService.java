package com.voicesecure.payments;

import java.util.Objects;
import java.util.UUID;

public final class PaymentSagaService {
    private final PaymentSagaRepository repository;

    public PaymentSagaService(PaymentSagaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public PaymentSaga start(PaymentRequest request, FraudDecision decision) {
        PaymentSaga existing = repository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.matchesRequest(request)) {
                throw new PaymentException("idempotency key reused with different payment request");
            }
            return existing;
        }

        PaymentSaga saga = PaymentSaga.initiate(request);
        PaymentSaga winner = repository.createIfAbsent(saga);
        if (winner != saga) {
            if (!winner.matchesRequest(request))
                throw new PaymentException("idempotency key reused with different payment request");
            return winner;
        }
        if (decision.approved()) {
            saga.approveFraud(decision);
        } else {
            saga.rejectFraud(decision);
        }
        repository.save(saga);
        return saga;
    }

    public PaymentSaga recordVoiceOutcome(UUID sagaId, VoiceOutcome outcome) {
        PaymentSaga saga = requireSaga(sagaId);
        if (saga.isTerminal()) {
            return saga;
        }
        if (saga.state() != PaymentSagaState.VOICE_VERIFICATION_PENDING) {
            return saga;
        }

        switch (outcome.status()) {
            case APPROVED -> saga.voiceApproved();
            case REJECTED -> saga.voiceRejected();
            case TIMEOUT -> saga.voiceTimedOut();
            case SPOOF_DETECTED -> {
                saga.voiceRejected();
            }
        }
        repository.save(saga);
        return saga;
    }

    public PaymentSaga find(UUID sagaId) {
        return requireSaga(sagaId);
    }

    public PaymentSaga recordFallbackOutcome(UUID sagaId, FallbackOutcome outcome) {
        PaymentSaga saga = requireSaga(sagaId);
        if (saga.isTerminal()) {
            return saga;
        }
        if (outcome.verified()) {
            saga.fallbackVerified(outcome);
        } else {
            saga.fallbackFailed(outcome);
        }
        repository.save(saga);
        return saga;
    }

    public PaymentSaga markFundsReserved(UUID sagaId) {
        PaymentSaga saga = requireSaga(sagaId);
        saga.fundsReserved();
        repository.save(saga);
        return saga;
    }

    public PaymentSaga failFundsReservation(UUID sagaId, String reason) {
        PaymentSaga saga = requireSaga(sagaId);
        saga.fundsReservationFailed(reason);
        repository.save(saga);
        return saga;
    }

    public PaymentSaga startLedgerCommit(UUID sagaId) {
        PaymentSaga saga = requireSaga(sagaId);
        saga.ledgerCommitStarted();
        repository.save(saga);
        return saga;
    }

    public PaymentSaga completeLedgerCommit(UUID sagaId) {
        PaymentSaga saga = requireSaga(sagaId);
        saga.ledgerCommitSucceeded();
        repository.save(saga);
        return saga;
    }

    public PaymentSaga failLedgerCommit(UUID sagaId, String reason) {
        PaymentSaga saga = requireSaga(sagaId);
        saga.ledgerCommitFailed(reason);
        repository.save(saga);
        return saga;
    }

    public PaymentSaga complete(UUID sagaId) {
        PaymentSaga saga = requireSaga(sagaId);
        saga.complete();
        repository.save(saga);
        return saga;
    }

    public PaymentSaga compensate(UUID sagaId) {
        PaymentSaga saga = requireSaga(sagaId);
        saga.compensationSucceeded();
        repository.save(saga);
        return saga;
    }

    public PaymentSaga failCompensation(UUID sagaId, String reason) {
        PaymentSaga saga = requireSaga(sagaId);
        saga.compensationFailed(reason);
        repository.save(saga);
        return saga;
    }

    private PaymentSaga requireSaga(UUID sagaId) {
        return repository.findBySagaId(sagaId)
                .orElseThrow(() -> new PaymentException("saga not found: " + sagaId));
    }
}
