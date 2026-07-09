package com.voicesecure.events;

public interface KafkaRecordPublisher {
    void publish(KafkaRecord record);
}
