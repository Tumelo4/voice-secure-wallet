package com.voicesecure.payments;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public final class PaymentRecoveryServiceTests {
    public static void main(String[] args) {
        InMemoryPaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
        PaymentSaga saga = PaymentSaga.initiate(new PaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                100, "ZAR", "trace-recovery"));
        saga.approveFraud(new FraudDecision(0.1, AuthPolicy.VOICE_ONLY, true, ""));
        saga.voiceApproved();
        saga.fundsReserved();
        saga.ledgerCommitStarted();
        saga.externalOutcomeUnknown("provider-ref");
        repository.save(saga);

        Clock future = Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneOffset.UTC);
        PaymentRecoveryService recovery = new PaymentRecoveryService(repository, future, Duration.ofMinutes(15));
        var stuck = recovery.scan();

        if (stuck.size() != 1) throw new AssertionError("one stuck payment expected");
        if (!"RECONCILIATION_STARTED".equals(stuck.get(0).action())) throw new AssertionError("reconciliation action expected");
        if (saga.state() != PaymentSagaState.RECONCILIATION_REQUIRED) throw new AssertionError("reconciliation state expected");
        System.out.println("Payment recovery service tests passed: 1");
    }
}
