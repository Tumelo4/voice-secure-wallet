package com.voicesecure.events;

public interface EventPublisher {
    void publish(EventEnvelope envelope);
}

