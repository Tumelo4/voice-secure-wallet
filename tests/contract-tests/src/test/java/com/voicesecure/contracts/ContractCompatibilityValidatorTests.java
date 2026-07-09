package com.voicesecure.contracts;

import java.util.List;
import java.util.Set;

public final class ContractCompatibilityValidatorTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("valid Pact and Schema Registry plan passes", ContractCompatibilityValidatorTests::validPlanPasses),
                new TestCase("Pact publication and consumer verification are required", ContractCompatibilityValidatorTests::pactPublicationAndConsumerVerificationAreRequired),
                new TestCase("Schema Registry compatibility gaps are blocked", ContractCompatibilityValidatorTests::schemaRegistryCompatibilityGapsAreBlocked),
                new TestCase("duplicate artifacts and blank consumers are blocked", ContractCompatibilityValidatorTests::duplicateArtifactsAndBlankConsumersAreBlocked)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Contract compatibility validator tests passed: " + tests.length);
    }

    private static void validPlanPasses() {
        ContractCompatibilityValidationReport report = new ContractCompatibilityValidator().validate(validPlan());

        assertTrue(report.ready(), "valid compatibility plan should be ready");
        assertEquals(0, report.blockers().size(), "valid plan blockers");
    }

    private static void pactPublicationAndConsumerVerificationAreRequired() {
        ContractCompatibilityPlan plan = new ContractCompatibilityPlan(
                false,
                true,
                false,
                List.of(
                        artifact("fraud.scored", false, false, true, true, SchemaCompatibilityMode.BACKWARD_TRANSITIVE),
                        artifact("compliance.hit", true, true, true, true, SchemaCompatibilityMode.BACKWARD_TRANSITIVE)
                )
        );

        ContractCompatibilityValidationReport report = new ContractCompatibilityValidator().validate(plan);

        assertTrue(!report.ready(), "Pact gaps should block compatibility readiness");
        assertTrue(report.blockers().contains("Pact broker must be reachable in CI"), "broker blocker");
        assertTrue(report.blockers().contains("CI must fail when Pact verification fails"), "CI blocker");
        assertTrue(report.blockers().contains("fraud.scored contract must be published to Pact broker"), "publication blocker");
        assertTrue(report.blockers().contains("fraud.scored consumers must verify the latest Pact"), "consumer verification blocker");
    }

    private static void schemaRegistryCompatibilityGapsAreBlocked() {
        ContractCompatibilityPlan plan = new ContractCompatibilityPlan(
                true,
                false,
                true,
                List.of(
                        artifact("fraud.scored", true, true, false, false, SchemaCompatibilityMode.NONE),
                        artifact("compliance.hit", true, true, true, true, SchemaCompatibilityMode.FORWARD)
                )
        );

        ContractCompatibilityValidationReport report = new ContractCompatibilityValidator().validate(plan);

        assertTrue(!report.ready(), "Schema Registry gaps should block compatibility readiness");
        assertTrue(report.blockers().contains("Schema Registry must be reachable in CI"), "registry blocker");
        assertTrue(report.blockers().contains("fraud.scored schema must be registered"), "schema registration blocker");
        assertTrue(report.blockers().contains("fraud.scored schema id must be pinned in release evidence"), "schema id blocker");
        assertTrue(report.blockers().contains("fraud.scored must use BACKWARD_TRANSITIVE schema compatibility"), "fraud compatibility blocker");
        assertTrue(report.blockers().contains("compliance.hit must use BACKWARD_TRANSITIVE schema compatibility"), "compliance compatibility blocker");
    }

    private static void duplicateArtifactsAndBlankConsumersAreBlocked() {
        ContractCompatibilityPlan plan = new ContractCompatibilityPlan(
                true,
                true,
                true,
                List.of(
                        artifact("fraud.scored", true, true, true, true, SchemaCompatibilityMode.BACKWARD_TRANSITIVE),
                        artifact(
                                "fraud.scored",
                                Set.of("payment-service", " "),
                                true,
                                true,
                                true,
                                true,
                                SchemaCompatibilityMode.BACKWARD_TRANSITIVE
                        ),
                        artifact("compliance.hit", true, true, true, true, SchemaCompatibilityMode.BACKWARD_TRANSITIVE)
                )
        );

        ContractCompatibilityValidationReport report = new ContractCompatibilityValidator().validate(plan);

        assertTrue(!report.ready(), "duplicate artifacts and blank consumers should block readiness");
        assertTrue(report.blockers().contains("fraud.scored contract artifact must be unique"), "duplicate artifact blocker");
        assertTrue(report.blockers().contains("fraud.scored consumers must not be blank"), "blank consumer blocker");
    }

    private static ContractCompatibilityPlan validPlan() {
        return new ContractCompatibilityPlan(
                true,
                true,
                true,
                List.of(
                        artifact("fraud.scored", true, true, true, true, SchemaCompatibilityMode.BACKWARD_TRANSITIVE),
                        artifact("compliance.hit", true, true, true, true, SchemaCompatibilityMode.BACKWARD_TRANSITIVE)
                )
        );
    }

    private static ContractArtifact artifact(
            String eventType,
            boolean pactPublished,
            boolean consumersVerified,
            boolean schemaRegistered,
            boolean schemaIdPinned,
            SchemaCompatibilityMode compatibilityMode
    ) {
        return artifact(
                eventType,
                Set.of("payment-service", "support-service"),
                pactPublished,
                consumersVerified,
                schemaRegistered,
                schemaIdPinned,
                compatibilityMode
        );
    }

    private static ContractArtifact artifact(
            String eventType,
            Set<String> consumers,
            boolean pactPublished,
            boolean consumersVerified,
            boolean schemaRegistered,
            boolean schemaIdPinned,
            SchemaCompatibilityMode compatibilityMode
    ) {
        return new ContractArtifact(
                eventType,
                eventType + "-value",
                consumers,
                pactPublished,
                consumersVerified,
                schemaRegistered,
                schemaIdPinned,
                compatibilityMode
        );
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
