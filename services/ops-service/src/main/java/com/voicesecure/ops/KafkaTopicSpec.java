package com.voicesecure.ops;

import java.util.Objects;

public record KafkaTopicSpec(
        String topicName,
        int partitions,
        int replicationFactor,
        SchemaCompatibility schemaCompatibility,
        boolean deadLetterQueueEnabled,
        boolean retentionConfigured
) {
    public KafkaTopicSpec {
        topicName = Objects.requireNonNull(topicName, "topicName").trim();
        schemaCompatibility = Objects.requireNonNull(schemaCompatibility, "schemaCompatibility");
        if (topicName.isEmpty()) {
            throw new OpsException("topic name is required");
        }
        if (partitions <= 0) {
            throw new OpsException("partitions must be positive");
        }
        if (replicationFactor <= 0) {
            throw new OpsException("replication factor must be positive");
        }
    }
}
