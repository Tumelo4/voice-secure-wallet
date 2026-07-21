package com.voicesecure.api;

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

/** Production Kafka/PostgreSQL composition for asynchronous notifications. */
public final class ProductionNotificationRuntime implements AutoCloseable {
    private final KafkaNotificationConsumer worker;

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
        NotificationService service = new NotificationService(
                new PostgresNotificationRepository(dataSource, "notification-service"),
                Objects.requireNonNull(otpGenerator, "otpGenerator"));
        worker = new KafkaNotificationConsumer(consumer, service);
    }

    public KafkaNotificationConsumer worker() { return worker; }

    @Override public void close() { worker.close(); }
}
