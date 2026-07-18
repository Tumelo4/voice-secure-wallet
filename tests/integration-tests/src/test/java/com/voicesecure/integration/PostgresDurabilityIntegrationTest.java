package com.voicesecure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.infrastructure.PostgresLedgerRepository;
import com.voicesecure.api.PaymentSettlementCoordinator;
import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.PaymentException;
import com.voicesecure.payments.PaymentRequest;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.payments.PostgresPaymentSagaRepository;
import com.voicesecure.payments.PaymentSagaState;
import com.voicesecure.payments.VoiceOutcome;
import com.voicesecure.payments.VoiceOutcomeStatus;
import java.time.Duration;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
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
    void concurrentPaymentStartsAcrossServiceInstancesConvergeOnOneDatabaseSaga() throws Exception {
        PaymentSagaService firstService = new PaymentSagaService(new PostgresPaymentSagaRepository(paymentDataSource));
        PaymentSagaService secondService = new PaymentSagaService(new PostgresPaymentSagaRepository(paymentDataSource));
        UUID idempotencyKey = UUID.randomUUID();
        UUID sagaId = UUID.nameUUIDFromBytes(("integration:" + idempotencyKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        PaymentRequest request = new PaymentRequest(sagaId, idempotencyKey, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 12_500L, "ZAR", "trace-multi-instance");
        FraudDecision decision = new FraudDecision(0.1, AuthPolicy.VOICE_ONLY, true, "approved");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<PaymentSaga> first = new AtomicReference<>();
        AtomicReference<PaymentSaga> second = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread one = new Thread(() -> startPayment(firstService, request, decision, ready, start, first, failure));
        Thread two = new Thread(() -> startPayment(secondService, request, decision, ready, start, second, failure));
        one.start(); two.start(); ready.await(); start.countDown(); one.join(); two.join();

        if (failure.get() != null) throw new AssertionError("concurrent payment start failed", failure.get());
        assertEquals(sagaId, first.get().sagaId());
        assertEquals(sagaId, second.get().sagaId());
        try (Connection connection = paymentDataSource.getConnection();
             var statement = connection.prepareStatement("SELECT count(*) FROM payment_sagas WHERE idempotency_key = ?")) {
            statement.setObject(1, idempotencyKey);
            try (var result = statement.executeQuery()) {
                result.next();
                assertEquals(1L, result.getLong(1));
            }
        }
    }

    @Test
    void settlementRecoversAfterReservationAndLedgerCommitCrashWindows() {
        PostgresPaymentSagaRepository paymentRepository = new PostgresPaymentSagaRepository(paymentDataSource);
        PaymentSagaService payments = new PaymentSagaService(paymentRepository);
        PostgresLedgerRepository ledgerRepository = new PostgresLedgerRepository(ledgerDataSource);
        LedgerService ledger = new LedgerService(ledgerRepository);
        PaymentSettlementCoordinator coordinator = new PaymentSettlementCoordinator(payments, ledger);

        PaymentSaga afterReservation = authorisedPayment(payments, ledger, 20_000L, "trace-after-reservation");
        ledger.reserveFunds(afterReservation.sagaId(), afterReservation.sagaId(), afterReservation.fromAccountId(),
                afterReservation.amount(), afterReservation.currency(), Duration.ofMinutes(15));
        assertEquals(PaymentSagaState.COMPLETED, coordinator.recover(afterReservation).state());

        PaymentSaga afterLedgerCommit = authorisedPayment(payments, ledger, 30_000L, "trace-after-ledger");
        ledger.reserveFunds(afterLedgerCommit.sagaId(), afterLedgerCommit.sagaId(), afterLedgerCommit.fromAccountId(),
                afterLedgerCommit.amount(), afterLedgerCommit.currency(), Duration.ofMinutes(15));
        payments.markFundsReserved(afterLedgerCommit.sagaId());
        payments.startLedgerCommit(afterLedgerCommit.sagaId());
        ledger.commitReservedTransfer(afterLedgerCommit.sagaId(), afterLedgerCommit.sagaId(),
                afterLedgerCommit.idempotencyKey(), afterLedgerCommit.fromAccountId(), afterLedgerCommit.toAccountId(),
                afterLedgerCommit.amount(), afterLedgerCommit.currency());
        PaymentSaga recovered = coordinator.recover(afterLedgerCommit);
        assertEquals(PaymentSagaState.COMPLETED, recovered.state(), recovered.events().toString());
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

    private static void startPayment(PaymentSagaService service, PaymentRequest request, FraudDecision decision,
                                     CountDownLatch ready, CountDownLatch start, AtomicReference<PaymentSaga> result,
                                     AtomicReference<Throwable> failure) {
        ready.countDown();
        try {
            start.await();
            result.set(service.start(request, decision));
        } catch (Throwable thrown) {
            failure.compareAndSet(null, thrown);
        }
    }

    private static PaymentSaga authorisedPayment(PaymentSagaService payments, LedgerService ledger,
                                                  long amount, String traceId) {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        ledger.createAccount(source, "ZAR", 100_000L);
        ledger.createAccount(destination, "ZAR", 0L);
        PaymentSaga saga = payments.start(new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        source, destination, amount, "ZAR", traceId),
                new FraudDecision(0.1, AuthPolicy.VOICE_ONLY, true, "approved"));
        return payments.recordVoiceOutcome(saga.sagaId(),
                new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.99, "matched"));
    }
}
