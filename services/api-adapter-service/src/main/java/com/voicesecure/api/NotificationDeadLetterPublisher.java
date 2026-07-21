package com.voicesecure.api;

import org.apache.kafka.clients.consumer.ConsumerRecord;

@FunctionalInterface
public interface NotificationDeadLetterPublisher {
    void publish(ConsumerRecord<String, String> record, RuntimeException failure);
}
