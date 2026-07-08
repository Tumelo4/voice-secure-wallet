package com.voicesecure.contracts;

import com.voicesecure.compliance.ComplianceHitEvent;
import com.voicesecure.compliance.ComplianceProfile;
import com.voicesecure.compliance.ComplianceScreeningResult;
import com.voicesecure.compliance.ComplianceService;
import com.voicesecure.compliance.InMemoryComplianceRepository;
import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventTopic;
import com.voicesecure.fraud.AuthPolicy;
import com.voicesecure.fraud.FraudAssessment;
import com.voicesecure.fraud.FraudScoredEvent;
import com.voicesecure.fraud.FraudService;
import com.voicesecure.fraud.FraudTransactionRequest;
import com.voicesecure.fraud.VelocityTracker;
import java.time.Instant;
import java.util.UUID;

public final class EventContractTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("contract: fraud.scored carries auth policy and threshold", EventContractTests::fraudScoredCarriesAuthPolicyAndThreshold),
                new TestCase("contract: compliance.hit carries PEP hit evidence", EventContractTests::complianceHitCarriesPepEvidence),
                new TestCase("contract: clear compliance result cannot publish compliance.hit", EventContractTests::clearComplianceCannotPublishHit)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Event contract tests passed: " + tests.length);
    }

    private static void fraudScoredCarriesAuthPolicyAndThreshold() {
        UUID userId = UUID.randomUUID();
        FraudTransactionRequest request = fraudRequest(userId, 7_500, 5_000);
        FraudService fraudService = new FraudService(new ComplianceService(new InMemoryComplianceRepository()), new VelocityTracker());
        FraudAssessment assessment = fraudService.evaluate(request);

        EventEnvelope envelope = FraudScoredEvent.from(request, assessment, "trace-contract-1").toEnvelope();

        assertEquals(EventTopic.FRAUD.topicName(), envelope.topic(), "fraud topic");
        assertEquals(userId.toString(), envelope.partitionKeyValue(), "fraud partition key");
        assertEquals("fraud.scored", envelope.eventType(), "fraud event type");
        assertContains(envelope.payload(), "\"authPolicy\":\"" + AuthPolicy.VOICE_OTP.name() + "\"", "auth policy");
        assertContains(envelope.payload(), "\"voiceThreshold\":", "voice threshold");
        assertContains(envelope.payload(), "\"approved\":true", "approved flag");
    }

    private static void complianceHitCarriesPepEvidence() {
        UUID userId = UUID.randomUUID();
        ComplianceService complianceService = new ComplianceService(new InMemoryComplianceRepository());
        complianceService.addPep("9001015009087", "domestic politically exposed person");

        ComplianceScreeningResult result = complianceService.screen(new ComplianceProfile(
                userId,
                "Naledi Mokoena",
                "9001015009087",
                "ZA",
                750
        ));

        EventEnvelope envelope = ComplianceHitEvent.from(result, "trace-contract-2").toEnvelope();

        assertEquals(EventTopic.COMPLIANCE.topicName(), envelope.topic(), "compliance topic");
        assertEquals(userId.toString(), envelope.partitionKeyValue(), "compliance partition key");
        assertEquals("compliance.hit", envelope.eventType(), "compliance event type");
        assertContains(envelope.payload(), "\"hitType\":\"PEP_MATCH\"", "hit type");
        assertContains(envelope.payload(), "\"reason\":\"pep match on national id\"", "reason");
        assertContains(envelope.payload(), "\"caseId\":\"" + result.caseId() + "\"", "case id");
    }

    private static void clearComplianceCannotPublishHit() {
        ComplianceService complianceService = new ComplianceService(new InMemoryComplianceRepository());
        ComplianceScreeningResult result = complianceService.screen(new ComplianceProfile(
                UUID.randomUUID(),
                "Clear Customer",
                "CLEAR-123",
                "ZA",
                50
        ));

        assertThrows(IllegalArgumentException.class, () -> ComplianceHitEvent.from(result, "trace-contract-3"), "clear result should not create hit event");
    }

    private static FraudTransactionRequest fraudRequest(UUID userId, long amount, long highValueThreshold) {
        return new FraudTransactionRequest(
                UUID.randomUUID(),
                new ComplianceProfile(userId, "Trusted Customer", "CLEAR-456", "ZA", amount),
                amount,
                "ZAR",
                UUID.randomUUID(),
                0.99,
                72,
                highValueThreshold,
                Instant.parse("2026-07-01T09:30:00Z")
        );
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertContains(String value, String expected, String message) {
        if (!value.contains(expected)) {
            throw new AssertionError(message + ": expected payload to contain " + expected + " but was " + value);
        }
    }

    private static void assertThrows(Class<? extends RuntimeException> expected, Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            if (expected.isInstance(ex)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got " + ex.getClass().getSimpleName(), ex);
        }
        throw new AssertionError(message + ": expected exception " + expected.getSimpleName());
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}
