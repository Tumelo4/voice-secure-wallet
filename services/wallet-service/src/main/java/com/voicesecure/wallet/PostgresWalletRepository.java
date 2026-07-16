package com.voicesecure.wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresWalletRepository implements WalletRepository {
    private final DataSource dataSource;
    public PostgresWalletRepository(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource, "dataSource"); }

    @Override public void saveAccount(WalletAccount account) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO wallet_accounts (account_id, user_id, display_name, currency, opened_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, account.accountId()); statement.setObject(2, account.userId());
            statement.setString(3, account.displayName()); statement.setString(4, account.currency());
            statement.setObject(5, java.sql.Timestamp.from(account.openedAt())); statement.executeUpdate();
        } catch (SQLException failure) { throw failure("save wallet account", failure); }
    }

    @Override public Optional<WalletAccount> findAccount(UUID accountId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT user_id, account_id, display_name, currency, opened_at FROM wallet_accounts WHERE account_id = ?
                """)) {
            statement.setObject(1, accountId);
            try (ResultSet row = statement.executeQuery()) { return row.next() ? Optional.of(account(row)) : Optional.empty(); }
        } catch (SQLException failure) { throw failure("find wallet account", failure); }
    }

    @Override public List<WalletAccount> accountsForUser(UUID userId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT user_id, account_id, display_name, currency, opened_at FROM wallet_accounts
                WHERE user_id = ? ORDER BY opened_at, account_id
                """)) {
            statement.setObject(1, userId); List<WalletAccount> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(account(rows)); }
            return List.copyOf(result);
        } catch (SQLException failure) { throw failure("list wallet accounts", failure); }
    }

    @Override public void saveBalance(WalletBalance balance) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO wallet_balances (account_id, currency, balance, version, updated_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE SET currency = EXCLUDED.currency, balance = EXCLUDED.balance,
                    version = EXCLUDED.version, updated_at = EXCLUDED.updated_at
                WHERE wallet_balances.version < EXCLUDED.version
                """)) {
            statement.setObject(1, balance.accountId()); statement.setString(2, balance.currency());
            statement.setLong(3, balance.balance()); statement.setLong(4, balance.version());
            statement.setObject(5, java.sql.Timestamp.from(balance.updatedAt())); statement.executeUpdate();
        } catch (SQLException failure) { throw failure("save wallet balance", failure); }
    }

    @Override public Optional<WalletBalance> findBalance(UUID accountId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT account_id, currency, balance, version, updated_at FROM wallet_balances WHERE account_id = ?
                """)) {
            statement.setObject(1, accountId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(new WalletBalance((UUID) row.getObject("account_id"),
                        row.getString("currency"), row.getLong("balance"), row.getLong("version"),
                        row.getTimestamp("updated_at").toInstant())) : Optional.empty();
            }
        } catch (SQLException failure) { throw failure("find wallet balance", failure); }
    }

    @Override public boolean hasProcessedEvent(UUID eventId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM wallet_processed_events WHERE event_id = ?")) {
            statement.setObject(1, eventId); try (ResultSet row = statement.executeQuery()) { return row.next(); }
        } catch (SQLException failure) { throw failure("find processed wallet event", failure); }
    }

    @Override public void markProcessedEvent(UUID eventId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO wallet_processed_events (event_id) VALUES (?) ON CONFLICT DO NOTHING")) {
            statement.setObject(1, eventId); statement.executeUpdate();
        } catch (SQLException failure) { throw failure("mark processed wallet event", failure); }
    }

    private static WalletAccount account(ResultSet row) throws SQLException {
        return new WalletAccount((UUID) row.getObject("user_id"), (UUID) row.getObject("account_id"),
                row.getString("display_name"), row.getString("currency"), row.getTimestamp("opened_at").toInstant());
    }
    private static WalletException failure(String operation, SQLException cause) {
        return new WalletException(operation + " failed: " + cause.getSQLState());
    }
}
