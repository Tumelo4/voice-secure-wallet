package com.voicesecure.acceptance;

public final class VoiceSecureBehaviorTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("Scenario: voice fallback keeps a legitimate payment moving", VoiceSecureBehaviorTests::voiceFallbackKeepsLegitimatePaymentMoving),
                new TestCase("Scenario: compliance hit blocks funds movement", VoiceSecureBehaviorTests::complianceHitBlocksFundsMovement),
                new TestCase("Scenario: wallet read model follows ledger truth", VoiceSecureBehaviorTests::walletReadModelFollowsLedgerTruth)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("VoiceSecure behavior tests passed: " + tests.length);
    }

    private static void voiceFallbackKeepsLegitimatePaymentMoving() {
        VoiceSecureScenario scenario = VoiceSecureScenario.givenTrustedCustomerWithWallets(1_000);

        PaymentOutcome outcome = scenario
                .whenHighValuePaymentNeedsOtpFallback(750)
                .thenOtpFallbackIsDelivered()
                .thenFallbackPaymentCompletes();

        assertEquals("COMPLETED", outcome.paymentState(), "payment state");
        assertEquals(250L, outcome.sourceBalance(), "source wallet balance");
        assertEquals(750L, outcome.destinationBalance(), "destination wallet balance");
        assertEquals(0L, outcome.reconciliationTotal(), "ledger must remain zero-sum");
        assertTrue(outcome.notificationCount() >= 2, "OTP and receipt notifications should be sent");
    }

    private static void complianceHitBlocksFundsMovement() {
        VoiceSecureScenario scenario = VoiceSecureScenario.givenPepCustomerWithWallets(1_000);

        PaymentOutcome outcome = scenario.whenPaymentIsScreened(300);

        assertEquals("FRAUD_REJECTED", outcome.paymentState(), "payment state");
        assertEquals(1_000L, outcome.sourceBalance(), "source wallet balance");
        assertEquals(0L, outcome.destinationBalance(), "destination wallet balance");
        assertEquals(0, outcome.ledgerEntryCount(), "blocked payment should not write ledger entries");
        assertEquals(0L, outcome.reconciliationTotal(), "ledger must remain zero-sum");
    }

    private static void walletReadModelFollowsLedgerTruth() {
        VoiceSecureScenario scenario = VoiceSecureScenario.givenTrustedCustomerWithWallets(900);

        PaymentOutcome outcome = scenario.whenVoiceApprovedPaymentCompletes(400);

        assertEquals("COMPLETED", outcome.paymentState(), "payment state");
        assertEquals(500L, outcome.sourceBalance(), "source wallet balance");
        assertEquals(400L, outcome.destinationBalance(), "destination wallet balance");
        assertEquals(outcome.ledgerEntryCount(), outcome.walletProjectionCount(), "wallet should project every ledger entry once");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}
