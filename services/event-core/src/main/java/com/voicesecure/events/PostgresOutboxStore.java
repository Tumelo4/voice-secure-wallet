package com.voicesecure.events;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresOutboxStore implements DurableOutboxStore {
    private static final Set<String> ALLOWED_TABLES = Set.of("outbox_events", "payment_outbox_events");
    private final DataSource dataSource;
    private final String table;
    private final String topic;
    private final String partitionKeyField;

    public PostgresOutboxStore(DataSource dataSource, String table, EventTopic topic) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (!ALLOWED_TABLES.contains(table)) throw new IllegalArgumentException("unsupported outbox table: " + table);
        this.table = table;
        EventTopic definition = Objects.requireNonNull(topic, "topic");
        this.topic = definition.topicName();
        this.partitionKeyField = definition.partitionKeyField();
    }

    @Override
    public List<OutboxMessage> claimPending(UUID workerId, int limit, Instant now, Duration lease) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lease, "lease");
        if (limit < 1 || limit > 1_000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        String sql = """
                WITH candidates AS (
                    SELECT id FROM %s
                    WHERE published_at IS NULL
                      AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                      AND (lease_until IS NULL OR lease_until <= ?)
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE %s event
                SET lease_owner = ?, lease_until = ?, publish_attempts = publish_attempts + 1
                FROM candidates
                WHERE event.id = candidates.id
                RETURNING event.id, event.aggregate_id, event.aggregate_type, event.event_type,
                          event.event_version, event.payload, event.trace_id, event.created_at,
                          event.published_at, event.publish_attempts, event.last_error
                """.formatted(table, table);
        return inTransaction(connection -> {
            List<OutboxMessage> messages = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setInt(3, limit);
                statement.setObject(4, workerId);
                statement.setTimestamp(5, Timestamp.from(now.plus(lease)));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) messages.add(read(result));
                }
            }
            return List.copyOf(messages);
        });
    }

    @Override
    public void markPublished(UUID eventId, UUID workerId, Instant publishedAt) {
        updateOwned(eventId, workerId, "published_at = ?, lease_owner = NULL, lease_until = NULL, last_error = NULL", publishedAt, null);
    }

    @Override
    public void markFailed(UUID eventId, UUID workerId, Instant failedAt, String error, Duration retryDelay) {
        Objects.requireNonNull(failedAt, "failedAt");
        Objects.requireNonNull(retryDelay, "retryDelay");
        updateOwned(eventId, workerId,
                "next_attempt_at = ?, lease_owner = NULL, lease_until = NULL, last_error = ?",
                failedAt.plus(retryDelay), error == null ? "publication failed" : error.substring(0, Math.min(error.length(), 2_000)));
    }

    private void updateOwned(UUID eventId, UUID workerId, String assignment, Instant timestamp, String error) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(workerId, "workerId");
        inTransaction(connection -> {
            String sql = "UPDATE " + table + " SET " + assignment + " WHERE id = ? AND lease_owner = ? AND published_at IS NULL";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(timestamp));
                int idIndex;
                if (error == null) {
                    idIndex = 2;
                } else {
                    statement.setString(2, error);
                    idIndex = 3;
                }
                statement.setObject(idIndex, eventId);
                statement.setObject(idIndex + 1, workerId);
                if (statement.executeUpdate() != 1) throw new EventEnvelopeException("outbox lease is no longer owned for event " + eventId);
            }
            return null;
        });
    }

    private OutboxMessage read(ResultSet result) throws SQLException {
        UUID eventId = result.getObject("id", UUID.class);
        UUID aggregateId = result.getObject("aggregate_id", UUID.class);
        Instant createdAt = result.getTimestamp("created_at").toInstant();
        Timestamp published = result.getTimestamp("published_at");
        String traceId = result.getString("trace_id");
        if (traceId == null || traceId.isBlank()) traceId = eventId.toString();
        EventEnvelope envelope = new EventEnvelope(eventId, topic, partitionKeyField, aggregateId.toString(),
                result.getString("event_type"), result.getString("event_version"), aggregateId,
                result.getString("aggregate_type"), createdAt, traceId, result.getString("payload"));
        return new OutboxMessage(envelope, createdAt, published == null ? null : published.toInstant(),
                result.getInt("publish_attempts"), result.getString("last_error"));
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T value = work.apply(connection);
                connection.commit();
                return value;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException ignored) { }
                if (exception instanceof RuntimeException runtime) throw runtime;
                throw new EventEnvelopeException("outbox database operation failed", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new EventEnvelopeException("outbox database connection failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> { T apply(Connection connection) throws SQLException; }
}
