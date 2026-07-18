package com.voicesecure.integration;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventTopic;
import com.voicesecure.notifications.DeterministicOtpGenerator;
import com.voicesecure.notifications.NotificationDelivery;
import com.voicesecure.notifications.NotificationService;
import com.voicesecure.notifications.PostgresNotificationRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
final class NotificationInboxIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    static PGSimpleDataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:services/notification-service/src/main/resources/db/migration")
                .load().migrate();
    }

    @Test
    void concurrentConsumersPersistOneDeliveryAndOneInboxReceipt() throws Exception {
        EventEnvelope event = EventEnvelopeFactory.create(
                EventTopic.PAYMENTS, UUID.randomUUID(), "Payment", "payment.completed",
                Instant.parse("2026-07-18T18:00:00Z"), "trace-inbox",
                "{\"userId\":\"user-123\",\"amount\":750,\"currency\":\"ZAR\"}");
        NotificationService first = service();
        NotificationService second = service();

        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> first.consume(event));
            var right = executor.submit(() -> second.consume(event));
            NotificationDelivery firstResult = left.get();
            NotificationDelivery secondResult = right.get();
            assertEquals(firstResult.deliveryId(), secondResult.deliveryId());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, count("notification_inbox"));
        assertEquals(1, count("notification_deliveries"));
    }

    private static NotificationService service() {
        return new NotificationService(
                new PostgresNotificationRepository(dataSource, "notification-service"),
                new DeterministicOtpGenerator("123456"));
    }

    private static int count(String table) throws Exception {
        String sql = switch (table) {
            case "notification_inbox" -> "SELECT count(*) FROM notification_inbox";
            case "notification_deliveries" -> "SELECT count(*) FROM notification_deliveries";
            default -> throw new IllegalArgumentException("unsupported table");
        };
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }
}
