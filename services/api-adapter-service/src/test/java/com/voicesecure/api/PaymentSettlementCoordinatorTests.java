package com.voicesecure.api;

import com.voicesecure.ledger.AccountBalance;
import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.infrastructure.InMemoryLedgerRepository;
import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentRequest;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.payments.PaymentSagaState;
import com.voicesecure.payments.VoiceOutcome;
import com.voicesecure.payments.VoiceOutcomeStatus;
import java.util.UUID;

public final class PaymentSettlementCoordinatorTests {
    public static void main(String[] args) {
        approvedPaymentSettlesExactlyOnce();
        insufficientFundsBecomeTerminal();
        System.out.println("Payment settlement coordinator tests passed: 2");
    }

    private static void approvedPaymentSettlesExactlyOnce() {
        Fixture fixture = fixture(1_000);
        PaymentSaga authorised = fixture.authorised(400);
        PaymentSaga completed = fixture.coordinator.settle(authorised);
        PaymentSaga retry = fixture.coordinator.settle(completed);
        assertEquals(PaymentSagaState.COMPLETED, completed.state(), "completed state");
        assertEquals(PaymentSagaState.COMPLETED, retry.state(), "idempotent retry state");
        assertBalance(fixture, 600, 400);
        assertEquals(2, fixture.ledgerRepository.entries().size(), "single ledger batch");
    }

    private static void insufficientFundsBecomeTerminal() {
        Fixture fixture = fixture(100);
        PaymentSaga failed = fixture.coordinator.settle(fixture.authorised(400));
        assertEquals(PaymentSagaState.FUNDS_RESERVATION_FAILED, failed.state(), "reservation failure state");
        assertBalance(fixture, 100, 0);
        assertEquals(0, fixture.ledgerRepository.entries().size(), "no ledger postings");
    }

    private static Fixture fixture(long sourceBalance) {
        UUID source = UUID.randomUUID(); UUID destination = UUID.randomUUID();
        InMemoryLedgerRepository ledgerRepository = new InMemoryLedgerRepository();
        LedgerService ledger = new LedgerService(ledgerRepository);
        ledger.createAccount(source, "ZAR", sourceBalance); ledger.createAccount(destination, "ZAR", 0);
        PaymentSagaService payments = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        return new Fixture(source, destination, payments, ledgerRepository,
                new PaymentSettlementCoordinator(payments, ledger));
    }

    private static void assertBalance(Fixture fixture, long source, long destination) {
        AccountBalance sourceBalance = fixture.ledgerRepository.balances().get(fixture.source);
        AccountBalance destinationBalance = fixture.ledgerRepository.balances().get(fixture.destination);
        assertEquals(source, sourceBalance.balance(), "source balance");
        assertEquals(0L, sourceBalance.reservedBalance(), "source reservation");
        assertEquals(destination, destinationBalance.balance(), "destination balance");
    }

    private record Fixture(UUID source, UUID destination, PaymentSagaService payments,
                           InMemoryLedgerRepository ledgerRepository, PaymentSettlementCoordinator coordinator) {
        private PaymentSaga authorised(long amount) {
            PaymentSaga saga = payments.start(new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    source, destination, amount, "ZAR", "trace-settlement"),
                    new FraudDecision(0.1, AuthPolicy.VOICE_OTP, true, "approved"));
            return payments.recordVoiceOutcome(saga.sagaId(),
                    new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.99, "matched"));
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + " but got " + actual);
    }
}
