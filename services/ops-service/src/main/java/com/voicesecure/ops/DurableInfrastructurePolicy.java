package com.voicesecure.ops;

import com.voicesecure.events.EventTopic;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record DurableInfrastructurePolicy(
        Set<String> requiredKafkaTopics,
        int minimumPartitions,
        int minimumReplicationFactor,
        SchemaCompatibility requiredSchemaCompatibility,
        int minimumPrivateSubnets
) {
    public DurableInfrastructurePolicy {
        Objects.requireNonNull(requiredKafkaTopics, "requiredKafkaTopics");
        requiredSchemaCompatibility = Objects.requireNonNull(requiredSchemaCompatibility, "requiredSchemaCompatibility");
        requiredKafkaTopics = Set.copyOf(requiredKafkaTopics);
        if (requiredKafkaTopics.isEmpty()) {
            throw new OpsException("required Kafka topics cannot be empty");
        }
        if (minimumPartitions <= 0) {
            throw new OpsException("minimum partitions must be positive");
        }
        if (minimumReplicationFactor <= 0) {
            throw new OpsException("minimum replication factor must be positive");
        }
        if (minimumPrivateSubnets <= 0) {
            throw new OpsException("minimum private subnets must be positive");
        }
    }

    public static DurableInfrastructurePolicy defaults() {
        return new DurableInfrastructurePolicy(
                Arrays.stream(EventTopic.values())
                        .map(EventTopic::topicName)
                        .collect(Collectors.toUnmodifiableSet()),
                3,
                3,
                SchemaCompatibility.BACKWARD_TRANSITIVE,
                2
        );
    }
}
