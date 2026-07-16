package com.voicesecure.payments;

import com.voicesecure.events.EventPublisher;
import com.voicesecure.events.EventTopic;
import com.voicesecure.events.OutboxRelayWorker;
import com.voicesecure.events.OutboxRelayTelemetry;
import com.voicesecure.events.OutboxRetryPolicy;
import com.voicesecure.events.PostgresOutboxStore;
import com.voicesecure.events.TransactionalOutboxRelay;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;

public final class PaymentProductionRuntime implements AutoCloseable {
    private final PaymentSagaService paymentService;
    private final PaymentRecoveryService recoveryService;
    private final OutboxRelayWorker relayWorker;
    private final PaymentRecoveryWorker recoveryWorker;
    private final OutboxRelayTelemetry relayTelemetry;

    public PaymentProductionRuntime(DataSource dataSource, EventPublisher publisher, Clock clock,
                                    Consumer<RuntimeException> relayFailureHandler) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(clock, "clock");
        PostgresPaymentSagaRepository repository = new PostgresPaymentSagaRepository(dataSource);
        paymentService = new PaymentSagaService(repository);
        recoveryService = new PaymentRecoveryService(repository, clock, Duration.ofMinutes(5));
        recoveryWorker = new PaymentRecoveryWorker(
                new PostgresPaymentRecoveryCoordinator(dataSource, recoveryService, clock),
                Duration.ofSeconds(30), relayFailureHandler);
        relayTelemetry = new OutboxRelayTelemetry();
        TransactionalOutboxRelay relay = new TransactionalOutboxRelay(
                new PostgresOutboxStore(dataSource, "payment_outbox_events", EventTopic.PAYMENTS), publisher,
                UUID.randomUUID(), clock, Duration.ofSeconds(30),
                new OutboxRetryPolicy(Duration.ofSeconds(5), Duration.ofMinutes(5), 8, 0.2),
                100, relayTelemetry);
        relayWorker = new OutboxRelayWorker("payment-outbox-relay", relay, Duration.ofMillis(500), relayFailureHandler);
    }

    public PaymentSagaService paymentService() { return paymentService; }
    public PaymentRecoveryService recoveryService() { return recoveryService; }
    public OutboxRelayTelemetry.Snapshot relayTelemetry() { return relayTelemetry.snapshot(); }
    @Override public void close() {
        recoveryWorker.close();
        relayWorker.close();
    }
}
