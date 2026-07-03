package com.voicesecure.ops;

import java.util.List;
import java.util.Objects;

public record DurableInfrastructurePlan(
        List<KafkaTopicSpec> kafkaTopics,
        AwsInfrastructureSpec aws
) {
    public DurableInfrastructurePlan {
        Objects.requireNonNull(kafkaTopics, "kafkaTopics");
        aws = Objects.requireNonNull(aws, "aws");
        kafkaTopics = List.copyOf(kafkaTopics);
        if (kafkaTopics.isEmpty()) {
            throw new OpsException("Kafka topics cannot be empty");
        }
    }
}
