package com.voicesecure.payments;

import java.util.List;
import java.util.UUID;

public final class PaymentSagaTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("happy path reaches COMPLETED with expected events", PaymentSagaTests::happyPathCompletes),
                new TestCase("voice timeout falls back to OTP", PaymentSagaTests::voiceTimeoutFallsBackToOtp),
                new TestCase("fraud rejection stops the saga early", PaymentSagaTests::fraudRejectionStopsEarly),
                new TestCase("funds reservation failure becomes terminal", PaymentSagaTests::fundsReservationFailureBecomesTerminal),
                new TestCase("ledger failure triggers compensation", PaymentSagaTests::ledgerFailureTriggersCompensation),
                new TestCase("compensation failure becomes critical", PaymentSagaTests::compensationFailureBecomesCritical),
                new TestCase("idempotent start returns the original saga", PaymentSagaTests::idempotentStartReturnsOriginalSaga),
                new TestCase("idempotent start rejects conflicting request", PaymentSagaTests::idempotentStartRejectsConflictingRequest),
                new TestCase("payment request requires trace id", PaymentSagaTests::paymentRequestRequiresTraceId)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Payment saga tests passed: " + tests.length);
    }

    private static void happyPathCompletes() {
        Fixture fixture = fixture();
        PaymentSaga saga = fixture.service.start(fixture.request, new FraudDecision(0.12, AuthPolicy.VOICE_ONLY, true, ""));
        fixture.service.recordVoiceOutcome(saga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.98, "voice matched"));
        fixture.service.markFundsReserved(saga.sagaId());
        fixture.service.startLedgerCommit(saga.sagaId());
        fixture.service.completeLedgerCommit(saga.sagaId());
        fixture.service.complete(saga.sagaId());

        assertEquals(PaymentSagaState.COMPLETED, saga.state(), "saga should complete");
        assertContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.completed", "completion event");
        assertContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.voice_approved", "voice approval event");
        assertEquals(true, saga.isTerminal(), "completed saga should be terminal");
    }

    private static void voiceTimeoutFallsBackToOtp() {
        Fixture fixture = fixture();
        PaymentRequest fallbackRequest = new PaymentRequest(
                fixture.request.sagaId(),
                fixture.request.idempotencyKey(),
                fixture.request.userId(),
                fixture.request.fromAccountId(),
                fixture.request.toAccountId(),
                fixture.request.amount(),
                fixture.request.currency(),
                fixture.request.traceId()
        );

        PaymentSaga saga = fixture.service.start(fallbackRequest, new FraudDecision(0.15, AuthPolicy.VOICE_OTP, true, ""));
        fixture.service.recordVoiceOutcome(saga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.TIMEOUT, 0.00, "timeout"));
        fixture.service.recordFallbackOutcome(saga.sagaId(), new FallbackOutcome(FallbackMethod.OTP, true, "otp verified"));
        fixture.service.markFundsReserved(saga.sagaId());

        assertEquals(PaymentSagaState.FUNDS_RESERVED, saga.state(), "fallback should lead to funds reserved");
        assertContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.voice_fallback_requested", "fallback request event");
        assertContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.voice_fallback_verified", "fallback verified event");
    }

    private static void fraudRejectionStopsEarly() {
        Fixture fixture = fixture();
        PaymentSaga saga = fixture.service.start(fixture.request, new FraudDecision(0.96, AuthPolicy.VOICE_ONLY, false, "high risk"));

        assertEquals(PaymentSagaState.FRAUD_REJECTED, saga.state(), "fraud rejection should be terminal");
        assertNotContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.funds_reserved", "no funds should move after fraud rejection");
    }

    private static void fundsReservationFailureBecomesTerminal() {
        Fixture fixture = fixture();
        PaymentSaga saga = fixture.service.start(fixture.request, new FraudDecision(0.08, AuthPolicy.VOICE_ONLY, true, ""));
        fixture.service.recordVoiceOutcome(saga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.99, "voice matched"));
        fixture.service.failFundsReservation(saga.sagaId(), "insufficient available funds");

        assertEquals(PaymentSagaState.FUNDS_RESERVATION_FAILED, saga.state(), "funds reservation failure should be terminal");
        assertContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.funds_reservation_failed", "funds reservation failure event");
        assertEquals(true, saga.isTerminal(), "funds reservation failure should be terminal");
    }

    private static void ledgerFailureTriggersCompensation() {
        Fixture fixture = fixture();
        PaymentSaga saga = fixture.service.start(fixture.request, new FraudDecision(0.08, AuthPolicy.VOICE_ONLY, true, ""));
        fixture.service.recordVoiceOutcome(saga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.99, "voice matched"));
        fixture.service.markFundsReserved(saga.sagaId());
        fixture.service.startLedgerCommit(saga.sagaId());
        fixture.service.failLedgerCommit(saga.sagaId(), "db unavailable");
        fixture.service.compensate(saga.sagaId());

        assertEquals(PaymentSagaState.COMPENSATED, saga.state(), "compensation should complete");
        assertContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.compensation_initiated", "compensation initiated event");
        assertContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.compensated", "compensated event");
    }

    private static void compensationFailureBecomesCritical() {
        Fixture fixture = fixture();
        PaymentSaga saga = fixture.service.start(fixture.request, new FraudDecision(0.08, AuthPolicy.VOICE_ONLY, true, ""));
        fixture.service.recordVoiceOutcome(saga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.99, "voice matched"));
        fixture.service.markFundsReserved(saga.sagaId());
        fixture.service.startLedgerCommit(saga.sagaId());
        fixture.service.failLedgerCommit(saga.sagaId(), "db unavailable");
        fixture.service.failCompensation(saga.sagaId(), "repair api down");

        assertEquals(PaymentSagaState.COMPENSATION_FAILED, saga.state(), "compensation failure should be critical");
        assertContains(saga.events().stream().map(PaymentEvent::eventType).toList(), "payment.compensation_failed", "compensation failed event");
    }

    private static void idempotentStartReturnsOriginalSaga() {
        Fixture fixture = fixture();
        PaymentSaga first = fixture.service.start(fixture.request, new FraudDecision(0.10, AuthPolicy.VOICE_ONLY, true, ""));
        PaymentSaga second = fixture.service.start(fixture.request, new FraudDecision(0.99, AuthPolicy.VOICE_ONLY, false, "ignored"));

        assertSame(first, second, "idempotent start should return the existing saga");
        assertEquals(1L, fixture.repository.findByIdempotencyKey(fixture.request.idempotencyKey()).stream().count(), "repository should hold one saga");
    }

    private static void idempotentStartRejectsConflictingRequest() {
        Fixture fixture = fixture();
        fixture.service.start(fixture.request, new FraudDecision(0.10, AuthPolicy.VOICE_ONLY, true, ""));
        PaymentRequest conflicting = new PaymentRequest(
                fixture.request.sagaId(),
                fixture.request.idempotencyKey(),
                fixture.request.userId(),
                fixture.request.fromAccountId(),
                fixture.request.toAccountId(),
                fixture.request.amount() + 1,
                fixture.request.currency(),
                fixture.request.traceId()
        );

        assertThrows(
                PaymentException.class,
                () -> fixture.service.start(conflicting, new FraudDecision(0.10, AuthPolicy.VOICE_ONLY, true, "")),
                "same idempotency key with different amount should fail"
        );
    }

    private static void paymentRequestRequiresTraceId() {
        assertThrows(
                PaymentException.class,
                () -> new PaymentRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        500,
                        "ZAR",
                        " "
                ),
                "blank trace id should fail"
        );
    }

    private static Fixture fixture() {
        InMemoryPaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
        PaymentSagaService service = new PaymentSagaService(repository);
        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                500,
                "ZAR",
                "trace-123"
        );
        return new Fixture(service, repository, request);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertContains(List<String> events, String expected, String message) {
        if (!events.contains(expected)) {
            throw new AssertionError(message + ": missing " + expected + " in " + events);
        }
    }

    private static void assertNotContains(List<String> events, String unexpected, String message) {
        if (events.contains(unexpected)) {
            throw new AssertionError(message + ": unexpected " + unexpected + " in " + events);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got " + actual, actual);
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    private record Fixture(PaymentSagaService service, InMemoryPaymentSagaRepository repository, PaymentRequest request) {
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}
