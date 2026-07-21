package com.voicesecure.api;

import com.voicesecure.events.EventEnvelopeException;
import com.voicesecure.events.KafkaClientRecordPublisher;
import com.voicesecure.events.KafkaRecord;
import com.voicesecure.events.EventTopic;
import com.voicesecure.notifications.NotificationException;
import com.voicesecure.notifications.NotificationService;
import com.voicesecure.notifications.OtpGenerator;
import com.voicesecure.notifications.PostgresNotificationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.time.Duration;

/** Production Kafka/PostgreSQL composition for asynchronous notifications. */
public final class ProductionNotificationRuntime implements AutoCloseable {
    private final KafkaNotificationConsumer worker;
    private final KafkaClientRecordPublisher deadLetterPublisher;

    public ProductionNotificationRuntime(DataSource dataSource, Map<String, ?> kafkaConfiguration,
                                         OtpGenerator otpGenerator) {
        Objects.requireNonNull(dataSource, "dataSource");
        Properties properties = new Properties();
        properties.putAll(Objects.requireNonNull(kafkaConfiguration, "kafkaConfiguration"));
        properties.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.putIfAbsent(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of("payments", "voice"));
        deadLetterPublisher = new KafkaClientRecordPublisher(kafkaConfiguration, Duration.ofSeconds(10));
        NotificationService service = new NotificationService(
                new PostgresNotificationRepository(dataSource, "notification-service"),
                Objects.requireNonNull(otpGenerator, "otpGenerator"));
        worker = new KafkaNotificationConsumer(consumer, service,
                failure -> failure instanceof EventEnvelopeException
                        || failure instanceof NotificationException && failure.getCause() == null,
                (record, failure) -> deadLetterPublisher.publish(new KafkaRecord(
                        EventTopic.NOTIFICATION_DLQ.topicName(), record.key(), record.value(), Map.of(
                        "sourceTopic", record.topic(),
                        "sourcePartition", Integer.toString(record.partition()),
                        "sourceOffset", Long.toString(record.offset()),
                        "failureType", failure.getClass().getSimpleName()))));
    }

    public KafkaNotificationConsumer worker() { return worker; }

    @Override public void close() {
        try {
            worker.close();
        } finally {
            deadLetterPublisher.close();
        }
    }
}
