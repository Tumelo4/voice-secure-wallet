package com.voicesecure.api;

import com.voicesecure.ledger.LedgerException;
import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.payments.PaymentSagaState;
import com.voicesecure.payments.PaymentRecoveryAction;
import java.time.Duration;
import java.util.Objects;

public final class PaymentSettlementCoordinator implements PaymentSettlementHandler, PaymentRecoveryAction {
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);
    private final PaymentSagaService payments;
    private final LedgerService ledger;

    public PaymentSettlementCoordinator(PaymentSagaService payments, LedgerService ledger) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    @Override
    public synchronized PaymentSaga settle(PaymentSaga supplied) {
        PaymentSaga saga = payments.find(Objects.requireNonNull(supplied, "saga").sagaId());
        if (saga.isTerminal()) return saga;
        if (saga.state() == PaymentSagaState.FUNDS_RESERVING) {
            try {
                ledger.reserveFunds(saga.sagaId(), saga.sagaId(), saga.fromAccountId(),
                        saga.amount(), saga.currency(), RESERVATION_TTL);
                saga = payments.markFundsReserved(saga.sagaId());
            } catch (LedgerException failure) {
                return payments.failFundsReservation(saga.sagaId(), safeReason(failure));
            }
        }
        if (saga.state() == PaymentSagaState.FUNDS_RESERVED) {
            saga = payments.startLedgerCommit(saga.sagaId());
        }
        if (saga.state() == PaymentSagaState.LEDGER_COMMITTING) {
            try {
                ledger.commitReservedTransfer(saga.sagaId(), saga.sagaId(), saga.idempotencyKey(),
                        saga.fromAccountId(), saga.toAccountId(), saga.amount(), saga.currency());
                saga = payments.completeLedgerCommit(saga.sagaId());
            } catch (LedgerException failure) {
                saga = payments.failLedgerCommit(saga.sagaId(), safeReason(failure));
                try {
                    ledger.releaseFunds(saga.sagaId());
                    return payments.compensate(saga.sagaId());
                } catch (LedgerException compensationFailure) {
                    return payments.failCompensation(saga.sagaId(), safeReason(compensationFailure));
                }
            }
        }
        if (saga.state() == PaymentSagaState.COMPLETING) {
            saga = payments.complete(saga.sagaId());
        }
        if (saga.state() == PaymentSagaState.COMPENSATION_IN_PROGRESS) {
            try {
                ledger.releaseFunds(saga.sagaId());
                saga = payments.compensate(saga.sagaId());
            } catch (LedgerException failure) {
                saga = payments.failCompensation(saga.sagaId(), safeReason(failure));
            }
        }
        return saga;
    }

    @Override
    public PaymentSaga recover(PaymentSaga saga) {
        return settle(saga);
    }

    private static String safeReason(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
