package com.voicesecure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.voicesecure.ledger.LedgerService;
import com.voicesecure.ledger.PostgresLedgerRepository;
import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.PaymentException;
import com.voicesecure.payments.PaymentRequest;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.payments.PostgresPaymentSagaRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
final class PostgresDurabilityIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static PGSimpleDataSource ledgerDataSource;
    private static PGSimpleDataSource paymentDataSource;

    @BeforeAll
    static void migrate() {
        ledgerDataSource = migrateSchema("ledger", "services/ledger-service/src/main/resources/db/migration");
        paymentDataSource = migrateSchema("payment", "services/payment-service/src/main/resources/db/migration");
    }

    @Test
    void concurrentSagaCopiesUseOptimisticLockingAndSurviveRepositoryRestart() {
        PostgresPaymentSagaRepository repository = new PostgresPaymentSagaRepository(paymentDataSource);
        PaymentSagaService service = new PaymentSagaService(repository);
        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                25_000L, "ZAR", "trace-testcontainers");
        PaymentSaga started = service.start(request, new FraudDecision(0.1, AuthPolicy.VOICE_ONLY, true, "approved"));

        PostgresPaymentSagaRepository restarted = new PostgresPaymentSagaRepository(paymentDataSource);
        PaymentSaga firstCopy = restarted.findBySagaId(started.sagaId()).orElseThrow();
        PaymentSaga staleCopy = restarted.findBySagaId(started.sagaId()).orElseThrow();
        assertEquals(started.state(), firstCopy.state());

        firstCopy.voiceApproved();
        restarted.save(firstCopy);
        staleCopy.voiceApproved();
        assertThrows(PaymentException.class, () -> restarted.save(staleCopy));
    }

    @Test
    void ledgerEntriesAreBalancedReconstructableAndAppendOnlyInPostgres() throws SQLException {
        PostgresLedgerRepository repository = new PostgresLedgerRepository(ledgerDataSource);
        LedgerService ledger = new LedgerService(repository);
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        repository.createAccount(source, "ZAR", 100_000L);
        repository.createAccount(destination, "ZAR", 0L);
        ledger.transfer(UUID.randomUUID(), UUID.randomUUID(), source, destination, 40_000L, "ZAR");

        assertEquals(60_000L, repository.balances().get(source).balance());
        assertEquals(40_000L, repository.balances().get(destination).balance());
        assertEquals(0L, repository.entries().stream().mapToLong(entry -> entry.signedAmount()).sum());

        try (Connection connection = ledgerDataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE ledger_entries SET signed_amount = 1"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM ledger_entries"));
        }
    }

    private static PGSimpleDataSource migrateSchema(String schema, String relativeMigrationPath) {
        String location = "filesystem:" + Path.of(relativeMigrationPath).toAbsolutePath();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations(location)
                .load()
                .migrate();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setCurrentSchema(schema);
        return dataSource;
    }
}
