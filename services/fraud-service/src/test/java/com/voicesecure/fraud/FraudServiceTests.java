package com.voicesecure.fraud;

import com.voicesecure.compliance.ComplianceProfile;
import com.voicesecure.compliance.ComplianceScreeningResult;
import com.voicesecure.compliance.ComplianceService;
import com.voicesecure.compliance.ComplianceHitType;
import com.voicesecure.compliance.InMemoryComplianceRepository;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class FraudServiceTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("pep hit blocks payment and maps into payment saga rejection", FraudServiceTests::pepHitBlocksPayment),
                new TestCase("high value payment escalates to voice otp", FraudServiceTests::highValuePaymentEscalatesToVoiceOtp),
                new TestCase("low trust and velocity escalate to device pin", FraudServiceTests::lowTrustAndVelocityEscalate),
                new TestCase("custom policy and compliance port drive fraud decisions", FraudServiceTests::customPolicyAndPortDriveDecision)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Fraud service tests passed: " + tests.length);
    }

    private static void pepHitBlocksPayment() {
        InMemoryComplianceRepository complianceRepository = new InMemoryComplianceRepository();
        ComplianceService complianceService = new ComplianceService(complianceRepository);
        complianceService.addPep("9001015009087", "pep");
        FraudService fraudService = new FraudService(complianceService, new VelocityTracker());
        PaymentSagaService paymentSagaService = new PaymentSagaService(new InMemoryPaymentSagaRepository());

        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        FraudAssessment assessment = fraudService.evaluate(new FraudTransactionRequest(
                UUID.randomUUID(),
                new ComplianceProfile(userId, "Nomsa Dlamini", "9001015009087", "ZA", 1000),
                1000,
                "ZAR",
                deviceId,
                0.9,
                48,
                5000,
                Instant.parse("2026-06-18T10:00:00Z")
        ));

        assertTrue(!assessment.approved(), "pep hit must block the transaction");
        assertEquals(com.voicesecure.fraud.AuthPolicy.DEVICE_PIN, assessment.authPolicy(), "pep hit policy");
        FraudDecision paymentDecision = new FraudDecision(assessment.score(), com.voicesecure.payments.AuthPolicy.valueOf(assessment.authPolicy().name()), assessment.approved(), assessment.reason());
        PaymentSaga saga = paymentSagaService.start(
                new com.voicesecure.payments.PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(), 1000, "ZAR", "trace-1"),
                paymentDecision
        );
        assertEquals(com.voicesecure.payments.PaymentSagaState.FRAUD_REJECTED, saga.state(), "payment saga should be rejected");
    }

    private static void highValuePaymentEscalatesToVoiceOtp() {
        FraudService fraudService = new FraudService(new ComplianceService(new InMemoryComplianceRepository()), new VelocityTracker());
        FraudAssessment assessment = fraudService.evaluate(new FraudTransactionRequest(
                UUID.randomUUID(),
                new ComplianceProfile(UUID.randomUUID(), "John Citizen", "8001015009087", "ZA", 6000),
                6000,
                "ZAR",
                UUID.randomUUID(),
                0.95,
                12,
                5000,
                Instant.parse("2026-06-18T10:01:00Z")
        ));

        assertTrue(assessment.approved(), "high value transaction should still be approvable");
        assertEquals(com.voicesecure.fraud.AuthPolicy.VOICE_OTP, assessment.authPolicy(), "high value policy");
    }

    private static void lowTrustAndVelocityEscalate() {
        FraudService fraudService = new FraudService(new ComplianceService(new InMemoryComplianceRepository()), new VelocityTracker());
        UUID userId = UUID.randomUUID();
        ComplianceProfile profile = new ComplianceProfile(userId, "John Citizen", "8001015009087", "ZA", 100);
        Instant first = Instant.parse("2026-06-18T10:00:00Z");
        fraudService.evaluate(new FraudTransactionRequest(UUID.randomUUID(), profile, 100, "ZAR", UUID.randomUUID(), 0.25, 1, 5000, first));
        fraudService.evaluate(new FraudTransactionRequest(UUID.randomUUID(), profile, 100, "ZAR", UUID.randomUUID(), 0.25, 1, 5000, first.plusSeconds(30)));
        FraudAssessment assessment = fraudService.evaluate(new FraudTransactionRequest(UUID.randomUUID(), profile, 100, "ZAR", UUID.randomUUID(), 0.25, 1, 5000, first.plusSeconds(60)));

        assertEquals(com.voicesecure.fraud.AuthPolicy.DEVICE_PIN, assessment.authPolicy(), "low trust velocity policy");
        assertTrue(assessment.score() > 0.25, "risk score should be elevated");
    }

    private static void customPolicyAndPortDriveDecision() {
        FraudPolicy policy = new FraudPolicy(
                Duration.ofMinutes(1),
                0.20,
                0.05,
                0.10,
                100000.0,
                10,
                0.01,
                0.10,
                0.10,
                0.10,
                1,
                0.05,
                0.50,
                0.90,
                0.10,
                0.10,
                0.95,
                0.55,
                0.90
        );
        ComplianceScreeningPort clearCompliance = profile -> new ComplianceScreeningResult(
                UUID.randomUUID(),
                profile.userId(),
                ComplianceHitType.NONE,
                false,
                profile.fullName(),
                "clear",
                Instant.parse("2026-06-18T10:00:00Z")
        );
        FraudService fraudService = new FraudService(clearCompliance, new VelocityTracker(), policy);

        FraudAssessment assessment = fraudService.evaluate(new FraudTransactionRequest(
                UUID.randomUUID(),
                new ComplianceProfile(UUID.randomUUID(), "John Citizen", "8001015009087", "ZA", 100),
                100,
                "ZAR",
                UUID.randomUUID(),
                0.95,
                48,
                5000,
                Instant.parse("2026-06-18T10:00:00Z")
        ));

        assertTrue(assessment.approved(), "custom policy should approve the low risk request");
        assertEquals(com.voicesecure.fraud.AuthPolicy.VOICE_ONLY, assessment.authPolicy(), "custom policy auth");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
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
