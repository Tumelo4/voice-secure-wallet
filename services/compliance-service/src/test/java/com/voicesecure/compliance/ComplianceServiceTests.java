package com.voicesecure.compliance;

import java.util.UUID;

public final class ComplianceServiceTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("pep hit creates a blocking result and audit entry", ComplianceServiceTests::pepHitCreatesBlockingResult),
                new TestCase("aml threshold creates an aml flag", ComplianceServiceTests::amlThresholdCreatesFlag)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Compliance service tests passed: " + tests.length);
    }

    private static void pepHitCreatesBlockingResult() {
        InMemoryComplianceRepository repository = new InMemoryComplianceRepository();
        ComplianceService service = new ComplianceService(repository);
        service.addPep("9001015009087", "sanctioned pEP");
        service.setAmlThreshold(25000);

        ComplianceScreeningResult result = service.screen(new ComplianceProfile(
                UUID.randomUUID(),
                "Nomsa Dlamini",
                "9001015009087",
                "ZA",
                1000
        ));

        assertTrue(result.hit(), "pep result should hit");
        assertEquals(ComplianceHitType.PEP_MATCH, result.hitType(), "pep hit type");
        assertEquals(1, service.auditTrail().size(), "audit trail entry");
    }

    private static void amlThresholdCreatesFlag() {
        InMemoryComplianceRepository repository = new InMemoryComplianceRepository();
        ComplianceService service = new ComplianceService(repository);
        service.setAmlThreshold(1000);

        ComplianceScreeningResult result = service.screen(new ComplianceProfile(
                UUID.randomUUID(),
                "John Citizen",
                "8001015009087",
                "ZA",
                10_000
        ));

        assertTrue(result.hit(), "aml result should hit");
        assertEquals(ComplianceHitType.AML_FLAG, result.hitType(), "aml hit type");
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

