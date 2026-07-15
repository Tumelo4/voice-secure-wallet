package com.voicesecure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.voicesecure.events.DurableOutboxStore;
import com.voicesecure.events.EventEnvelopeException;
import com.voicesecure.events.EventTopic;
import com.voicesecure.events.KafkaClientRecordPublisher;
import com.voicesecure.events.KafkaEventPublisher;
import com.voicesecure.events.OutboxMessage;
import com.voicesecure.events.PostgresOutboxStore;
import com.voicesecure.events.TransactionalOutboxRelay;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
final class TransactionalOutboxCrashWindowIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    @Container static final RedpandaContainer REDPANDA = new RedpandaContainer(DockerImageName.parse(
            "docker.redpanda.com/redpandadata/redpanda:v25.1.3@sha256:ed78af37eeaf733deaf7201cb89d5317c51b6bd404447cb6fb2dfbf517b4d76c")
            .asCompatibleSubstituteFor("docker.redpanda.com/redpandadata/redpanda"));
    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void prepareDependencies() throws Exception {
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE payment_outbox_events (
                      id UUID PRIMARY KEY, aggregate_id UUID NOT NULL, aggregate_type TEXT NOT NULL,
                      event_type TEXT NOT NULL, event_version TEXT NOT NULL, payload TEXT NOT NULL,
                      trace_id TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ,
                      publish_attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ,
                      last_error TEXT, lease_owner UUID, lease_until TIMESTAMPTZ,
                      dead_lettered_at TIMESTAMPTZ, dead_letter_reason TEXT
                    )
                    """);
        }
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(EventTopic.PAYMENTS.topicName(), 1, (short) 1))).all().get();
        }
    }

    @Test
    void publishAcknowledgementCrashRedeliversButDownstreamEventIdDeduplicates() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment_outbox_events
                (id, aggregate_id, aggregate_type, event_type, event_version, payload, trace_id, created_at)
                VALUES (?, ?, 'Payment', 'payment.completed', '1', '{}', 'trace-crash-window', ?)
                """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, paymentId);
            statement.setObject(3, java.sql.Timestamp.from(NOW));
            statement.executeUpdate();
        }

        PostgresOutboxStore postgresStore = new PostgresOutboxStore(
                dataSource, "payment_outbox_events", EventTopic.PAYMENTS);
        DurableOutboxStore crashAfterPublish = new CrashOnceOnMarkPublishedStore(postgresStore);
        Map<String, Object> producerConfig = Map.of("bootstrap.servers", REDPANDA.getBootstrapServers());
        try (KafkaClientRecordPublisher records = new KafkaClientRecordPublisher(producerConfig, Duration.ofSeconds(10))) {
            TransactionalOutboxRelay firstWorker = relay(crashAfterPublish, records, UUID.randomUUID());
            assertEquals(1, firstWorker.relayOnce().failedCount(), "broker ack followed by persistence failure retries");
            TransactionalOutboxRelay restartedWorker = relay(postgresStore, records, UUID.randomUUID());
            assertEquals(1, restartedWorker.relayOnce().publishedCount(), "replacement worker republishes pending row");
        }

        List<String> deliveries = consume(2);
        Set<String> appliedEventIds = new HashSet<>();
        deliveries.forEach(value -> appliedEventIds.add(jsonString(value, "eventId")));
        assertEquals(2, deliveries.size(), "at-least-once relay exposes the crash-window duplicate");
        assertEquals(Set.of(eventId.toString()), appliedEventIds, "event-id inbox semantics collapse duplicate effects");
    }

    private static TransactionalOutboxRelay relay(DurableOutboxStore store, KafkaClientRecordPublisher records,
                                                   UUID workerId) {
        return new TransactionalOutboxRelay(store, new KafkaEventPublisher(records), workerId,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30), Duration.ofSeconds(5), 10);
    }

    private static List<String> consume(int expected) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "crash-window-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                properties, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(EventTopic.PAYMENTS.topicName()));
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (values.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(250)).forEach(record -> values.add(record.value()));
            }
            return List.copyOf(values);
        }
    }

    private static String jsonString(String value, String field) {
        String marker = "\"" + field + "\":\"";
        int start = value.indexOf(marker) + marker.length();
        return value.substring(start, value.indexOf('"', start));
    }

    private static final class CrashOnceOnMarkPublishedStore implements DurableOutboxStore {
        private final DurableOutboxStore delegate;
        private boolean crashed;
        private CrashOnceOnMarkPublishedStore(DurableOutboxStore delegate) { this.delegate = delegate; }
        @Override public List<OutboxMessage> claimPending(UUID workerId, int limit, Instant now, Duration lease) {
            return delegate.claimPending(workerId, limit, now, lease);
        }
        @Override public void markPublished(UUID eventId, UUID workerId, Instant publishedAt) {
            if (!crashed) {
                crashed = true;
                throw new EventEnvelopeException("simulated crash after broker acknowledgement");
            }
            delegate.markPublished(eventId, workerId, publishedAt);
        }
        @Override public void markFailed(UUID eventId, UUID workerId, Instant failedAt, String error,
                                         Duration retryDelay) {
            delegate.markFailed(eventId, workerId, failedAt, error, Duration.ZERO);
        }
        @Override public void markDeadLettered(UUID eventId, UUID workerId, Instant failedAt, String reason) {
            delegate.markDeadLettered(eventId, workerId, failedAt, reason);
        }
    }
}
