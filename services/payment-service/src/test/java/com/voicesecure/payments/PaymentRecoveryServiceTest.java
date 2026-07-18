package com.voicesecure.payments;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PaymentRecoveryServiceTest {
    @Test
    void unknownExternalStatusStartsReconciliation() {
        InMemoryPaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
        PaymentSaga saga = recoverableSaga("trace-recovery");
        saga.fundsReserved();
        saga.ledgerCommitStarted();
        saga.externalOutcomeUnknown("provider-ref");
        repository.save(saga);

        PaymentRecoveryService recovery = new PaymentRecoveryService(repository, futureClock(), Duration.ofMinutes(15));
        var stuck = recovery.scan();

        assertEquals(1, stuck.size());
        assertEquals("RECONCILIATION_STARTED", stuck.get(0).action());
        assertEquals(PaymentSagaState.RECONCILIATION_REQUIRED, saga.state());
    }

    @Test
    void recoverableSettlementStateInvokesAutomaticAction() {
        InMemoryPaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
        PaymentSaga saga = recoverableSaga("trace-automatic-recovery");
        repository.save(saga);
        int[] attempts = {0};
        PaymentRecoveryService recovery = new PaymentRecoveryService(
                repository, futureClock(), Duration.ofMinutes(15), value -> {
                    attempts[0]++;
                    return value;
                });

        var stuck = recovery.scan();

        assertEquals(1, stuck.size());
        assertEquals("AUTOMATIC_RECOVERY_ATTEMPTED", stuck.get(0).action());
        assertEquals(1, attempts[0]);
    }

    private static PaymentSaga recoverableSaga(String traceId) {
        PaymentSaga saga = PaymentSaga.initiate(new PaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                100, "ZAR", traceId));
        saga.approveFraud(new FraudDecision(0.1, AuthPolicy.VOICE_ONLY, true, ""));
        saga.voiceApproved();
        return saga;
    }

    private static Clock futureClock() {
        return Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneOffset.UTC);
    }
}
