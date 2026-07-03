package com.voicesecure.ops;

import com.voicesecure.events.EventTopic;
import java.util.List;
import java.util.Set;

public final class DurableInfrastructureValidatorTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("valid Kafka and AWS plan passes readiness checks", DurableInfrastructureValidatorTests::validPlanPasses),
                new TestCase("missing Kafka topics are blocked", DurableInfrastructureValidatorTests::missingKafkaTopicIsBlocked),
                new TestCase("weak Kafka durability settings are blocked", DurableInfrastructureValidatorTests::weakKafkaDurabilityIsBlocked),
                new TestCase("AWS high availability and encryption are required", DurableInfrastructureValidatorTests::awsHaAndEncryptionRequired),
                new TestCase("custom infrastructure policy controls topics and replication", DurableInfrastructureValidatorTests::customPolicyControlsTopics)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Durable infrastructure validator tests passed: " + tests.length);
    }

    private static void validPlanPasses() {
        DurableInfrastructureValidationReport report = new DurableInfrastructureValidator().validate(validPlan());

        assertTrue(report.ready(), "valid plan should be ready");
        assertEquals(0, report.blockers().size(), "valid plan blockers");
    }

    private static void missingKafkaTopicIsBlocked() {
        DurableInfrastructurePlan plan = new DurableInfrastructurePlan(
                topicsWithout(EventTopic.COMPLIANCE.topicName()),
                validAwsSpec()
        );

        DurableInfrastructureValidationReport report = new DurableInfrastructureValidator().validate(plan);

        assertTrue(!report.ready(), "missing topic plan should be blocked");
        assertTrue(report.blockers().contains("Kafka topic compliance is required"), "missing compliance topic blocker");
    }

    private static void weakKafkaDurabilityIsBlocked() {
        DurableInfrastructurePlan plan = new DurableInfrastructurePlan(
                List.of(new KafkaTopicSpec("payments", 1, 1, SchemaCompatibility.FORWARD, false, false)),
                validAwsSpec()
        );

        DurableInfrastructureValidationReport report = new DurableInfrastructureValidator(new DurableInfrastructurePolicy(
                Set.of("payments"),
                3,
                3,
                SchemaCompatibility.BACKWARD_TRANSITIVE,
                2
        )).validate(plan);

        assertTrue(!report.ready(), "weak topic should be blocked");
        assertTrue(report.blockers().contains("Kafka topic payments must have at least 3 partitions"), "partition blocker");
        assertTrue(report.blockers().contains("Kafka topic payments must have replication factor at least 3"), "replication blocker");
        assertTrue(report.blockers().contains("Kafka topic payments must use BACKWARD_TRANSITIVE schema compatibility"), "schema blocker");
        assertTrue(report.blockers().contains("Kafka topic payments must have a dead-letter queue"), "dlq blocker");
        assertTrue(report.blockers().contains("Kafka topic payments must define retention"), "retention blocker");
    }

    private static void awsHaAndEncryptionRequired() {
        DurableInfrastructurePlan plan = new DurableInfrastructurePlan(
                validTopics(),
                new AwsInfrastructureSpec(
                        "af-south-1",
                        1,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );

        DurableInfrastructureValidationReport report = new DurableInfrastructureValidator().validate(plan);

        assertTrue(!report.ready(), "weak AWS plan should be blocked");
        assertTrue(report.blockers().contains("AWS private subnet count must be at least 2"), "subnet blocker");
        assertTrue(report.blockers().contains("AWS KMS encryption must be enabled"), "kms blocker");
        assertTrue(report.blockers().contains("MSK TLS in transit must be enabled"), "msk tls blocker");
        assertTrue(report.blockers().contains("RDS point-in-time recovery must be enabled"), "pitr blocker");
        assertTrue(report.blockers().contains("S3 object lock must be enabled for audit evidence"), "object lock blocker");
        assertTrue(report.blockers().contains("Secrets must use managed references only"), "secrets blocker");
    }

    private static void customPolicyControlsTopics() {
        DurableInfrastructurePolicy policy = new DurableInfrastructurePolicy(
                Set.of("payments"),
                2,
                2,
                SchemaCompatibility.BACKWARD,
                2
        );
        DurableInfrastructurePlan plan = new DurableInfrastructurePlan(
                List.of(new KafkaTopicSpec("payments", 2, 2, SchemaCompatibility.BACKWARD, true, true)),
                validAwsSpec()
        );

        DurableInfrastructureValidationReport report = new DurableInfrastructureValidator(policy).validate(plan);

        assertTrue(report.ready(), "custom policy should accept narrower topic set");
    }

    private static DurableInfrastructurePlan validPlan() {
        return new DurableInfrastructurePlan(validTopics(), validAwsSpec());
    }

    private static List<KafkaTopicSpec> validTopics() {
        return List.of(
                topic(EventTopic.PAYMENTS),
                topic(EventTopic.LEDGER),
                topic(EventTopic.FRAUD),
                topic(EventTopic.VOICE),
                topic(EventTopic.COMPLIANCE),
                topic(EventTopic.IDENTITY),
                topic(EventTopic.SUPPORT),
                topic(EventTopic.RECOVERY)
        );
    }

    private static List<KafkaTopicSpec> topicsWithout(String topicName) {
        return validTopics().stream()
                .filter(topic -> !topic.topicName().equals(topicName))
                .toList();
    }

    private static KafkaTopicSpec topic(EventTopic topic) {
        return new KafkaTopicSpec(topic.topicName(), 6, 3, SchemaCompatibility.BACKWARD_TRANSITIVE, true, true);
    }

    private static AwsInfrastructureSpec validAwsSpec() {
        return new AwsInfrastructureSpec(
                "af-south-1",
                3,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true
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
