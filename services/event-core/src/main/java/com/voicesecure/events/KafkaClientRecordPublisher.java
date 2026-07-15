package com.voicesecure.events;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaClientRecordPublisher implements KafkaRecordPublisher, AutoCloseable {
    private final KafkaProducer<String, String> producer;
    private final Duration acknowledgementTimeout;

    public KafkaClientRecordPublisher(Map<String, ?> configuration, Duration acknowledgementTimeout) {
        Objects.requireNonNull(configuration, "configuration");
        this.acknowledgementTimeout = Objects.requireNonNull(acknowledgementTimeout, "acknowledgementTimeout");
        Properties properties = new Properties();
        properties.putAll(configuration);
        properties.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        properties.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.putIfAbsent(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
        producer = new KafkaProducer<>(properties);
    }

    @Override
    public void publish(KafkaRecord record) {
        Objects.requireNonNull(record, "record");
        ProducerRecord<String, String> kafkaRecord = new ProducerRecord<>(record.topic(), record.key(), record.value());
        record.headers().forEach((name, value) -> kafkaRecord.headers().add(
                new RecordHeader(name, value.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        try {
            producer.send(kafkaRecord).get(acknowledgementTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventEnvelopeException("Kafka publication interrupted", exception);
        } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new EventEnvelopeException("Kafka publication failed", exception);
        }
    }

    @Override public void close() { producer.close(acknowledgementTimeout); }
}
