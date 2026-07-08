package com.voicesecure.payments;

import java.util.List;
import java.util.UUID;

public final class PaymentSagaSnapshotTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("snapshot round-trips saga state and events", PaymentSagaSnapshotTests::snapshotRoundTripsState),
                new TestCase("rehydrated saga preserves terminal markers", PaymentSagaSnapshotTests::rehydratedSagaPreservesTerminalMarkers)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Payment saga snapshot tests passed: " + tests.length);
    }

    private static void snapshotRoundTripsState() {
        PaymentSaga saga = PaymentSaga.initiate(request());
        saga.approveFraud(new FraudDecision(0.12, AuthPolicy.VOICE_ONLY, true, ""));
        saga.voiceApproved();

        PaymentSagaSnapshot snapshot = saga.snapshot();
        PaymentSaga rehydrated = PaymentSaga.rehydrate(snapshot);

        assertEquals(saga.sagaId(), rehydrated.sagaId(), "saga id");
        assertEquals(saga.state(), rehydrated.state(), "state");
        assertEquals(saga.stateHistory(), rehydrated.stateHistory(), "state history");
        assertEquals(saga.events().size(), rehydrated.events().size(), "event count");
        assertEquals(List.of("payment.initiated", "payment.fraud_check_requested", "payment.fraud_approved", "payment.voice_requested", "payment.voice_approved"),
                rehydrated.events().stream().map(PaymentEvent::eventType).toList(),
                "event sequence");
    }

    private static void rehydratedSagaPreservesTerminalMarkers() {
        PaymentSaga saga = PaymentSaga.initiate(request());
        saga.rejectFraud(new FraudDecision(0.97, AuthPolicy.VOICE_ONLY, false, "high risk"));

        PaymentSaga rehydrated = PaymentSaga.rehydrate(saga.snapshot());

        assertEquals(true, rehydrated.isTerminal(), "terminal state");
        assertEquals(PaymentSagaState.FRAUD_REJECTED, rehydrated.state(), "terminal state value");
        assertEquals(0, rehydrated.completedAt() == null ? 0 : 1, "completedAt should remain empty");
    }

    private static PaymentRequest request() {
        return new PaymentRequest(
                UUID.fromString("aaaaaaaa-1111-2222-3333-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-1111-2222-3333-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-1111-2222-3333-cccccccccccc"),
                UUID.fromString("dddddddd-1111-2222-3333-dddddddddddd"),
                UUID.fromString("eeeeeeee-1111-2222-3333-eeeeeeeeeeee"),
                500,
                "ZAR",
                "trace-payment-snapshot"
        );
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}
