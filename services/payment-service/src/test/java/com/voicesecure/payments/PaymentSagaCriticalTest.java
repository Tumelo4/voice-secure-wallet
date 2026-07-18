package com.voicesecure.payments;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PaymentSagaCriticalTest {
    @Test
    void duplicateStartReturnsTheOriginalSaga() {
        InMemoryPaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
        PaymentSagaService service = new PaymentSagaService(repository);
        PaymentRequest request = request(10_000L);

        PaymentSaga first = service.start(request, approved());
        PaymentSaga retry = service.start(request, new FraudDecision(0.99, AuthPolicy.VOICE_ONLY, false, "ignored"));

        assertSame(first, retry);
        assertEquals(first.sagaId(), repository.findByIdempotencyKey(request.idempotencyKey()).orElseThrow().sagaId());
    }

    @Test
    void conflictingIdempotentStartIsRejected() {
        PaymentSagaService service = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        PaymentRequest request = request(10_000L);
        service.start(request, approved());
        PaymentRequest conflict = new PaymentRequest(request.sagaId(), request.idempotencyKey(), request.userId(),
                request.fromAccountId(), request.toAccountId(), 10_001L, request.currency(), request.traceId());

        assertThrows(PaymentException.class, () -> service.start(conflict, approved()));
    }

    @Test
    void ledgerFailureRequiresCompensationBeforeTerminalState() {
        PaymentSagaService service = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        PaymentSaga saga = service.start(request(10_000L), approved());
        service.recordVoiceOutcome(saga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.99, "matched"));
        service.markFundsReserved(saga.sagaId());
        service.startLedgerCommit(saga.sagaId());
        service.failLedgerCommit(saga.sagaId(), "database unavailable");

        assertEquals(PaymentSagaState.COMPENSATION_IN_PROGRESS, saga.state());
        service.compensate(saga.sagaId());
        assertEquals(PaymentSagaState.COMPENSATED, saga.state());
    }

    private static PaymentRequest request(long amount) {
        return new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), amount, "ZAR", "trace-native-junit");
    }

    private static FraudDecision approved() {
        return new FraudDecision(0.1, AuthPolicy.VOICE_ONLY, true, "approved");
    }
}
