package com.voicesecure.api;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresPaymentReferenceRegistry implements PaymentReferenceRegistry {
    private final DataSource dataSource;
    private final SecureRandom random = new SecureRandom();

    public PostgresPaymentReferenceRegistry(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public String referenceFor(UUID sagaId, UUID customerId) {
        String existing = findBySaga(sagaId).orElse(null);
        if (existing != null) return existing;
        for (int attempt = 0; attempt < 3; attempt++) {
            String reference = newReference();
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement(
                         "INSERT INTO customer_payment_references(payment_reference,saga_id,customer_id) VALUES (?,?,?)")) {
                statement.setString(1, reference);
                statement.setObject(2, sagaId);
                statement.setObject(3, customerId);
                statement.executeUpdate();
                return reference;
            } catch (SQLException exception) {
                existing = findBySaga(sagaId).orElse(null);
                if (existing != null) return existing;
                if (attempt == 2) throw new IllegalStateException("unable to persist payment reference", exception);
            }
        }
        throw new IllegalStateException("unable to persist payment reference");
    }

    @Override
    public Optional<RegisteredPayment> find(String paymentReference) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT saga_id,customer_id FROM customer_payment_references WHERE payment_reference=?")) {
            statement.setString(1, paymentReference);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(new RegisteredPayment(
                        result.getObject(1, UUID.class), result.getObject(2, UUID.class))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("unable to read payment reference", exception);
        }
    }

    private Optional<String> findBySaga(UUID sagaId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT payment_reference FROM customer_payment_references WHERE saga_id=?")) {
            statement.setObject(1, sagaId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("unable to read payment reference", exception);
        }
    }

    private String newReference() {
        byte[] entropy = new byte[12];
        random.nextBytes(entropy);
        return "VSW-" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }
}
