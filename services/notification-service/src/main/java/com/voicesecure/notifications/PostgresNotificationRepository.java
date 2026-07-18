package com.voicesecure.notifications;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Atomically persists a notification delivery and its consumer-inbox receipt. */
public final class PostgresNotificationRepository implements NotificationRepository {
    private final DataSource dataSource;
    private final String consumerName;

    public PostgresNotificationRepository(DataSource dataSource, String consumerName) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName is required");
        }
        this.consumerName = consumerName;
    }

    @Override
    public NotificationDelivery saveIfUnprocessed(NotificationDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int claimed = claimEvent(connection, delivery.sourceEventId());
                NotificationDelivery result;
                if (claimed == 1) {
                    insertDelivery(connection, delivery);
                    result = delivery;
                } else {
                    result = findBySourceEvent(connection, delivery.sourceEventId());
                }
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new NotificationException("notification inbox persistence failed", exception);
        }
    }

    @Override
    public List<NotificationDelivery> deliveries() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT * FROM notification_deliveries ORDER BY created_at, delivery_id");
             ResultSet rows = statement.executeQuery()) {
            List<NotificationDelivery> deliveries = new ArrayList<>();
            while (rows.next()) deliveries.add(map(rows));
            return List.copyOf(deliveries);
        } catch (SQLException exception) {
            throw new NotificationException("notification delivery query failed", exception);
        }
    }

    private int claimEvent(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO notification_inbox(consumer_name,event_id,processed_at)
                VALUES (?,?,now()) ON CONFLICT(consumer_name,event_id) DO NOTHING
                """)) {
            statement.setString(1, consumerName);
            statement.setObject(2, eventId);
            return statement.executeUpdate();
        }
    }

    private static void insertDelivery(Connection connection, NotificationDelivery delivery) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO notification_deliveries
                (delivery_id,source_event_id,source_event_type,channel,recipient_ref,trace_id,message,created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """)) {
            statement.setObject(1, delivery.deliveryId());
            statement.setObject(2, delivery.sourceEventId());
            statement.setString(3, delivery.sourceEventType());
            statement.setString(4, delivery.channel().name());
            statement.setString(5, delivery.recipientRef());
            statement.setString(6, delivery.traceId());
            statement.setString(7, delivery.message());
            statement.setTimestamp(8, Timestamp.from(delivery.createdAt()));
            statement.executeUpdate();
        }
    }

    private static NotificationDelivery findBySourceEvent(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM notification_deliveries WHERE source_event_id=?")) {
            statement.setObject(1, eventId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new NotificationException("processed event has no delivery record");
                return map(row);
            }
        }
    }

    private static NotificationDelivery map(ResultSet row) throws SQLException {
        return new NotificationDelivery(
                row.getObject("delivery_id", UUID.class), row.getObject("source_event_id", UUID.class),
                row.getString("source_event_type"), NotificationChannel.valueOf(row.getString("channel")),
                row.getString("recipient_ref"), row.getString("trace_id"), row.getString("message"),
                row.getTimestamp("created_at").toInstant());
    }
}
