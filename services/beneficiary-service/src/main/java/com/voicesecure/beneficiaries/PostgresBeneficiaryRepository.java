package com.voicesecure.beneficiaries;

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

public final class PostgresBeneficiaryRepository implements BeneficiaryRepository {
    private final DataSource dataSource;
    public PostgresBeneficiaryRepository(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource, "dataSource"); }

    @Override public void save(Beneficiary value) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO beneficiaries (beneficiary_id, customer_id, destination_account_id, display_name,
                    masked_account_number, currency, status, available_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (beneficiary_id) DO UPDATE SET display_name = EXCLUDED.display_name,
                    masked_account_number = EXCLUDED.masked_account_number, status = EXCLUDED.status,
                    available_at = EXCLUDED.available_at
                """)) {
            bind(statement, value); statement.executeUpdate();
        } catch (SQLException failure) { throw failure("save beneficiary", failure); }
    }

    @Override public Optional<Beneficiary> find(UUID id) { return findOne("beneficiary_id", id); }

    @Override public Optional<Beneficiary> findByCustomerAndDestination(UUID customerId, UUID destinationAccountId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM beneficiaries WHERE customer_id = ? AND destination_account_id = ?")) {
            statement.setObject(1, customerId); statement.setObject(2, destinationAccountId);
            try (ResultSet row = statement.executeQuery()) { return row.next() ? Optional.of(map(row)) : Optional.empty(); }
        } catch (SQLException failure) { throw failure("find beneficiary destination", failure); }
    }

    @Override public List<Beneficiary> findByCustomer(UUID customerId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM beneficiaries WHERE customer_id = ? ORDER BY created_at, beneficiary_id")) {
            statement.setObject(1, customerId); List<Beneficiary> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(map(rows)); }
            return List.copyOf(result);
        } catch (SQLException failure) { throw failure("list beneficiaries", failure); }
    }

    private Optional<Beneficiary> findOne(String column, UUID value) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM beneficiaries WHERE " + column + " = ?")) {
            statement.setObject(1, value); try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }
        } catch (SQLException failure) { throw failure("find beneficiary", failure); }
    }

    private static void bind(PreparedStatement statement, Beneficiary value) throws SQLException {
        statement.setObject(1, value.beneficiaryId()); statement.setObject(2, value.customerId());
        statement.setObject(3, value.destinationAccountId()); statement.setString(4, value.displayName());
        statement.setString(5, value.maskedAccountNumber()); statement.setString(6, value.currency());
        statement.setString(7, value.status().name()); statement.setObject(8, java.sql.Timestamp.from(value.availableAt()));
        statement.setObject(9, java.sql.Timestamp.from(value.createdAt()));
    }
    private static Beneficiary map(ResultSet row) throws SQLException {
        return new Beneficiary((UUID) row.getObject("beneficiary_id"), (UUID) row.getObject("customer_id"),
                (UUID) row.getObject("destination_account_id"), row.getString("display_name"),
                row.getString("masked_account_number"), row.getString("currency"),
                BeneficiaryStatus.valueOf(row.getString("status")), row.getTimestamp("available_at").toInstant(),
                row.getTimestamp("created_at").toInstant());
    }
    private static BeneficiaryException failure(String operation, SQLException cause) {
        return new BeneficiaryException(operation + " failed: " + cause.getSQLState());
    }
}
