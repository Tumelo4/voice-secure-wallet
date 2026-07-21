package com.voicesecure.events;

import java.util.Objects;

public final class KafkaEventPublisher implements EventPublisher {
    private final KafkaRecordPublisher publisher;

    public KafkaEventPublisher(KafkaRecordPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void publish(EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        publisher.publish(new KafkaRecord(
                envelope.topic(),
                envelope.partitionKeyValue(),
                EventEnvelopeCodec.encode(envelope),
                EventEnvelopeJson.headers(envelope)
        ));
    }
}
