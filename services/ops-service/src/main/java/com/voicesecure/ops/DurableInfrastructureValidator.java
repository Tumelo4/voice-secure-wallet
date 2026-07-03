package com.voicesecure.ops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DurableInfrastructureValidator {
    private final DurableInfrastructurePolicy policy;

    public DurableInfrastructureValidator() {
        this(DurableInfrastructurePolicy.defaults());
    }

    public DurableInfrastructureValidator(DurableInfrastructurePolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public DurableInfrastructureValidationReport validate(DurableInfrastructurePlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<String> blockers = new ArrayList<>();
        validateKafka(plan, blockers);
        validateAws(plan.aws(), blockers);
        return new DurableInfrastructureValidationReport(blockers.isEmpty(), blockers);
    }

    private void validateKafka(DurableInfrastructurePlan plan, List<String> blockers) {
        Map<String, KafkaTopicSpec> topics = new LinkedHashMap<>();
        for (KafkaTopicSpec topic : plan.kafkaTopics()) {
            topics.put(topic.topicName(), topic);
        }

        for (String requiredTopic : policy.requiredKafkaTopics().stream().sorted().toList()) {
            KafkaTopicSpec topic = topics.get(requiredTopic);
            if (topic == null) {
                blockers.add("Kafka topic " + requiredTopic + " is required");
                continue;
            }
            validateTopic(topic, blockers);
        }
    }

    private void validateTopic(KafkaTopicSpec topic, List<String> blockers) {
        if (topic.partitions() < policy.minimumPartitions()) {
            blockers.add("Kafka topic " + topic.topicName() + " must have at least " + policy.minimumPartitions() + " partitions");
        }
        if (topic.replicationFactor() < policy.minimumReplicationFactor()) {
            blockers.add("Kafka topic " + topic.topicName() + " must have replication factor at least " + policy.minimumReplicationFactor());
        }
        if (topic.schemaCompatibility() != policy.requiredSchemaCompatibility()) {
            blockers.add("Kafka topic " + topic.topicName() + " must use " + policy.requiredSchemaCompatibility() + " schema compatibility");
        }
        if (!topic.deadLetterQueueEnabled()) {
            blockers.add("Kafka topic " + topic.topicName() + " must have a dead-letter queue");
        }
        if (!topic.retentionConfigured()) {
            blockers.add("Kafka topic " + topic.topicName() + " must define retention");
        }
    }

    private void validateAws(AwsInfrastructureSpec aws, List<String> blockers) {
        if (aws.privateSubnetCount() < policy.minimumPrivateSubnets()) {
            blockers.add("AWS private subnet count must be at least " + policy.minimumPrivateSubnets());
        }
        require(aws.kmsEnabled(), "AWS KMS encryption must be enabled", blockers);
        require(aws.mskTlsInTransit(), "MSK TLS in transit must be enabled", blockers);
        require(aws.mskIamAuthEnabled(), "MSK IAM authentication must be enabled", blockers);
        require(aws.rdsMultiAz(), "RDS Multi-AZ must be enabled", blockers);
        require(aws.rdsPointInTimeRecovery(), "RDS point-in-time recovery must be enabled", blockers);
        require(aws.rdsDeletionProtection(), "RDS deletion protection must be enabled", blockers);
        require(aws.redisMultiAz(), "Redis Multi-AZ must be enabled", blockers);
        require(aws.redisEncryptionAtRest(), "Redis encryption at rest must be enabled", blockers);
        require(aws.redisEncryptionInTransit(), "Redis encryption in transit must be enabled", blockers);
        require(aws.s3ObjectLockEnabled(), "S3 object lock must be enabled for audit evidence", blockers);
        require(aws.secretsManagerReferencesOnly(), "Secrets must use managed references only", blockers);
    }

    private static void require(boolean condition, String blocker, List<String> blockers) {
        if (!condition) {
            blockers.add(blocker);
        }
    }
}
