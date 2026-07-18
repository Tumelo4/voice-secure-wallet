package com.voicesecure.payments;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public final class PaymentRecoveryServiceTests {
    public static void main(String[] args) {
        unknownExternalStatusStartsReconciliation();
        recoverableSettlementStateInvokesAutomaticAction();
        System.out.println("Payment recovery service tests passed: 2");
    }

    private static void unknownExternalStatusStartsReconciliation() {
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
    }

    private static void recoverableSettlementStateInvokesAutomaticAction() {
        InMemoryPaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
        PaymentSaga saga = PaymentSaga.initiate(new PaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                100, "ZAR", "trace-automatic-recovery"));
        saga.approveFraud(new FraudDecision(0.1, AuthPolicy.VOICE_ONLY, true, ""));
        saga.voiceApproved();
        repository.save(saga);
        int[] attempts = {0};
        Clock future = Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneOffset.UTC);
        PaymentRecoveryService recovery = new PaymentRecoveryService(repository, future, Duration.ofMinutes(15), value -> {
            attempts[0]++;
            return value;
        });

        var stuck = recovery.scan();
        if (stuck.size() != 1) throw new AssertionError("one recoverable payment expected");
        if (!"AUTOMATIC_RECOVERY_ATTEMPTED".equals(stuck.get(0).action()))
            throw new AssertionError("automatic recovery action expected");
        if (attempts[0] != 1) throw new AssertionError("automatic recovery must run once");
    }
}
