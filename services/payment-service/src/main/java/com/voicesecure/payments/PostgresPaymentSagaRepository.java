package com.voicesecure.payments;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresPaymentSagaRepository implements PaymentSagaRepository {
    private static final String SELECT_SAGA_BY_ID = """
            SELECT saga_id, idempotency_key, user_id, from_account_id, to_account_id, amount, currency,
                   trace_id, created_at, updated_at, completed_at, fraud_score, auth_policy,
                   fallback_method, state, state_history, persisted_event_count, version
            FROM payment_sagas
            WHERE saga_id = ?
            """;
    private static final String SELECT_SAGA_BY_IDEMPOTENCY = """
            SELECT saga_id, idempotency_key, user_id, from_account_id, to_account_id, amount, currency,
                   trace_id, created_at, updated_at, completed_at, fraud_score, auth_policy,
                   fallback_method, state, state_history, persisted_event_count, version
            FROM payment_sagas
            WHERE idempotency_key = ?
            """;
    private static final String SELECT_EVENTS = """
            SELECT event_id, saga_id, event_sequence, event_type, occurred_at, trace_id, payload
            FROM payment_saga_events
            WHERE saga_id = ?
            ORDER BY event_sequence
            """;
    private static final String SELECT_PERSISTED_EVENT_COUNT = """
            SELECT persisted_event_count
            FROM payment_sagas
            WHERE saga_id = ?
            FOR UPDATE
            """;
    private static final String SELECT_STUCK_SAGA_IDS = """
            SELECT saga_id FROM payment_sagas
            WHERE completed_at IS NULL AND updated_at < ?
              AND state NOT IN (
                'FRAUD_REJECTED', 'VOICE_VERIFICATION_TIMEOUT', 'VOICE_REJECTED',
                'VOICE_FALLBACK_FAILED', 'FUNDS_RESERVATION_FAILED', 'COMPLETED',
                'COMPENSATED', 'COMPENSATION_FAILED', 'FAILED'
              )
            ORDER BY updated_at, saga_id
            """;
    private static final String INSERT_SAGA = """
            INSERT INTO payment_sagas (
                saga_id,
                idempotency_key,
                user_id,
                from_account_id,
                to_account_id,
                amount,
                currency,
                trace_id,
                state,
                state_history,
                fraud_score,
                auth_policy,
                fallback_method,
                created_at,
                updated_at,
                completed_at,
                persisted_event_count,
                version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_SAGA = """
            UPDATE payment_sagas
            SET idempotency_key = ?,
                user_id = ?,
                from_account_id = ?,
                to_account_id = ?,
                amount = ?,
                currency = ?,
                trace_id = ?,
                state = ?,
                state_history = ?,
                fraud_score = ?,
                auth_policy = ?,
                fallback_method = ?,
                updated_at = ?,
                completed_at = ?,
                persisted_event_count = ?,
                version = ?
            WHERE saga_id = ? AND version = ?
            """;
    private static final String INSERT_EVENT = """
            INSERT INTO payment_saga_events (
                event_id,
                saga_id,
                event_sequence,
                event_type,
                occurred_at,
                trace_id,
                payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresPaymentSagaRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<PaymentSaga> findBySagaId(UUID sagaId) {
        return load(SELECT_SAGA_BY_ID, sagaId);
    }

    @Override
    public Optional<PaymentSaga> findByIdempotencyKey(UUID idempotencyKey) {
        return load(SELECT_SAGA_BY_IDEMPOTENCY, idempotencyKey);
    }

    @Override
    public List<PaymentSaga> findNonTerminalUpdatedBefore(Instant cutoff) {
        List<UUID> ids = inTransaction(connection -> {
            List<UUID> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_STUCK_SAGA_IDS)) {
                statement.setTimestamp(1, Timestamp.from(Objects.requireNonNull(cutoff, "cutoff")));
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) result.add(resultSet.getObject(1, UUID.class));
                }
            }
            return List.copyOf(result);
        });
        List<PaymentSaga> sagas = new ArrayList<>();
        for (UUID id : ids) findBySagaId(id).ifPresent(sagas::add);
        return List.copyOf(sagas);
    }

    @Override
    public void save(PaymentSaga saga) {
        Objects.requireNonNull(saga, "saga");
        inTransaction(connection -> {
            if (exists(connection, saga.sagaId())) {
                update(connection, saga);
                return null;
            }
            insert(connection, saga);
            return null;
        });
        saga.markPersisted();
    }

    private Optional<PaymentSaga> load(String sql, UUID key) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, key);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    PaymentSagaSnapshot snapshot = snapshot(resultSet);
                    List<PaymentEvent> events = loadEvents(connection, snapshot.sagaId());
                    return Optional.of(PaymentSaga.rehydrate(new PaymentSagaSnapshot(
                            snapshot.sagaId(),
                            snapshot.idempotencyKey(),
                            snapshot.userId(),
                            snapshot.fromAccountId(),
                            snapshot.toAccountId(),
                            snapshot.amount(),
                            snapshot.currency(),
                            snapshot.traceId(),
                            snapshot.createdAt(),
                            snapshot.updatedAt(),
                            snapshot.completedAt(),
                            snapshot.version(),
                            snapshot.fraudScore(),
                            snapshot.authPolicy(),
                            snapshot.fallbackMethod(),
                            snapshot.state(),
                            snapshot.stateHistory(),
                            events
                    )));
                }
            }
        });
    }

    private void insert(Connection connection, PaymentSaga saga) throws SQLException {
        int persistedEventCount = saga.events().size();
        PaymentSagaSnapshot snapshot = saga.snapshot();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SAGA)) {
            bindSaga(statement, snapshot, persistedEventCount);
            statement.executeUpdate();
        }
        insertEvents(connection, saga, 0);
    }

    private void update(Connection connection, PaymentSaga saga) throws SQLException {
        int persistedEventCount = readPersistedEventCount(connection, saga.sagaId());
        List<PaymentEvent> events = saga.events();
        if (events.size() < persistedEventCount) {
            throw new PaymentException("stored saga events are ahead of in-memory state");
        }

        PaymentSagaSnapshot snapshot = saga.snapshot();
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_SAGA)) {
            bindSagaUpdate(statement, snapshot, events.size());
            statement.setObject(17, saga.sagaId());
            statement.setLong(18, saga.persistedVersion());
            if (statement.executeUpdate() != 1) {
                throw new PaymentException("payment saga was concurrently modified");
            }
        }
        insertEvents(connection, saga, persistedEventCount);
    }

    private boolean exists(Connection connection, UUID sagaId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PERSISTED_EVENT_COUNT)) {
            statement.setObject(1, sagaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private int readPersistedEventCount(Connection connection, UUID sagaId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PERSISTED_EVENT_COUNT)) {
            statement.setObject(1, sagaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new PaymentException("saga not found: " + sagaId);
                }
                return resultSet.getInt(1);
            }
        }
    }

    private List<PaymentEvent> loadEvents(Connection connection, UUID sagaId) throws SQLException {
        List<PaymentEvent> events = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_EVENTS)) {
            statement.setObject(1, sagaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(new PaymentEvent(
                            resultSet.getObject("event_id", UUID.class),
                            resultSet.getObject("saga_id", UUID.class),
                            resultSet.getString("event_type"),
                            resultSet.getTimestamp("occurred_at").toInstant(),
                            resultSet.getString("trace_id"),
                            resultSet.getString("payload")
                    ));
                }
            }
        }
        return List.copyOf(events);
    }

    private PaymentSagaSnapshot snapshot(ResultSet resultSet) throws SQLException {
        return new PaymentSagaSnapshot(
                resultSet.getObject("saga_id", UUID.class),
                resultSet.getObject("idempotency_key", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("from_account_id", UUID.class),
                resultSet.getObject("to_account_id", UUID.class),
                resultSet.getLong("amount"),
                resultSet.getString("currency"),
                resultSet.getString("trace_id"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                timestampOrNull(resultSet, "completed_at"),
                resultSet.getLong("version"),
                resultSet.getDouble("fraud_score"),
                enumOrNull(resultSet.getString("auth_policy"), AuthPolicy.class),
                enumOrNull(resultSet.getString("fallback_method"), FallbackMethod.class),
                enumValue(resultSet.getString("state"), PaymentSagaState.class),
                parseStateHistory(resultSet.getString("state_history")),
                List.of()
        );
    }

    private void insertEvents(Connection connection, PaymentSaga saga, int startingIndex) throws SQLException {
        List<PaymentEvent> events = saga.events();
        for (int i = startingIndex; i < events.size(); i++) {
            PaymentEvent event = events.get(i);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT)) {
                statement.setObject(1, event.eventId());
                statement.setObject(2, event.sagaId());
                statement.setLong(3, i + 1L);
                statement.setString(4, event.eventType());
                statement.setTimestamp(5, Timestamp.from(event.occurredAt()));
                statement.setString(6, event.traceId());
                statement.setString(7, event.payload());
                statement.executeUpdate();
            }
        }
    }

    private void bindSaga(PreparedStatement statement, PaymentSagaSnapshot snapshot, int persistedEventCount) throws SQLException {
        statement.setObject(1, snapshot.sagaId());
        statement.setObject(2, snapshot.idempotencyKey());
        statement.setObject(3, snapshot.userId());
        statement.setObject(4, snapshot.fromAccountId());
        statement.setObject(5, snapshot.toAccountId());
        statement.setLong(6, snapshot.amount());
        statement.setString(7, snapshot.currency());
        statement.setString(8, snapshot.traceId());
        statement.setString(9, snapshot.state().name());
        statement.setString(10, joinStateHistory(snapshot.stateHistory()));
        statement.setDouble(11, snapshot.fraudScore());
        setNullableEnum(statement, 12, snapshot.authPolicy());
        setNullableEnum(statement, 13, snapshot.fallbackMethod());
        statement.setTimestamp(14, Timestamp.from(snapshot.createdAt()));
        statement.setTimestamp(15, Timestamp.from(snapshot.updatedAt()));
        setNullableTimestamp(statement, 16, snapshot.completedAt());
        statement.setLong(17, persistedEventCount);
        statement.setLong(18, snapshot.version());
    }

    private void bindSagaUpdate(PreparedStatement statement, PaymentSagaSnapshot snapshot, int persistedEventCount) throws SQLException {
        statement.setObject(1, snapshot.idempotencyKey());
        statement.setObject(2, snapshot.userId());
        statement.setObject(3, snapshot.fromAccountId());
        statement.setObject(4, snapshot.toAccountId());
        statement.setLong(5, snapshot.amount());
        statement.setString(6, snapshot.currency());
        statement.setString(7, snapshot.traceId());
        statement.setString(8, snapshot.state().name());
        statement.setString(9, joinStateHistory(snapshot.stateHistory()));
        statement.setDouble(10, snapshot.fraudScore());
        setNullableEnum(statement, 11, snapshot.authPolicy());
        setNullableEnum(statement, 12, snapshot.fallbackMethod());
        statement.setTimestamp(13, Timestamp.from(snapshot.updatedAt()));
        setNullableTimestamp(statement, 14, snapshot.completedAt());
        statement.setLong(15, persistedEventCount);
        statement.setLong(16, snapshot.version());
    }

    private static void setNullableEnum(PreparedStatement statement, int index, Enum<?> value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value.name());
        }
    }

    private static void setNullableTimestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static <T extends Enum<T>> T enumOrNull(String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return enumValue(value, type);
    }

    private static <T extends Enum<T>> T enumValue(String value, Class<T> type) {
        return Enum.valueOf(type, value);
    }

    private static List<PaymentSagaState> parseStateHistory(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] states = raw.split(",");
        List<PaymentSagaState> history = new ArrayList<>(states.length);
        for (String state : states) {
            history.add(PaymentSagaState.valueOf(state.trim()));
        }
        return List.copyOf(history);
    }

    private static String joinStateHistory(List<PaymentSagaState> history) {
        StringJoiner joiner = new StringJoiner(",");
        for (PaymentSagaState state : history) {
            joiner.add(state.name());
        }
        return joiner.toString();
    }

    private static Instant timestampOrNull(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort only.
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw new PaymentException("database operation failed", ex);
            } catch (RuntimeException ex) {
                rollbackQuietly(connection);
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new PaymentException("database connection failed", ex);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
