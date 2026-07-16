package com.voicesecure.payments;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresPaymentRecoveryCoordinator implements PaymentRecoveryCoordinator {
    private static final long ADVISORY_LOCK_ID = 0x5653575041594cL;
    private final DataSource dataSource;
    private final PaymentRecoveryService recovery;
    private final Clock clock;

    public PostgresPaymentRecoveryCoordinator(DataSource dataSource, PaymentRecoveryService recovery, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<StuckPayment> runOnce() {
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryLock(lockConnection)) return List.of();
            try {
                List<StuckPayment> found = recovery.scan();
                persistAudit(lockConnection, found);
                return found;
            } finally {
                unlock(lockConnection);
            }
        } catch (SQLException failure) {
            throw new PaymentException("payment recovery coordination failed", failure);
        }
    }

    private static boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, ADVISORY_LOCK_ID);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private void persistAudit(Connection connection, List<StuckPayment> found) throws SQLException {
        if (found.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment_recovery_audit
                (id, saga_id, observed_state, recovery_action, trace_id, observed_at, age_millis)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (StuckPayment payment : found) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, payment.sagaId());
                statement.setString(3, payment.state().name());
                statement.setString(4, payment.action());
                statement.setString(5, "recovery:" + payment.sagaId());
                statement.setObject(6, java.sql.Timestamp.from(clock.instant()));
                statement.setLong(7, payment.age().toMillis());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, ADVISORY_LOCK_ID);
            statement.execute();
        }
    }
}
