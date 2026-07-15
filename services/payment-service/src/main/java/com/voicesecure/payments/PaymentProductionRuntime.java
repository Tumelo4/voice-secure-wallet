package com.voicesecure.payments;

import com.voicesecure.events.EventPublisher;
import com.voicesecure.events.EventTopic;
import com.voicesecure.events.OutboxRelayWorker;
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

    public PaymentProductionRuntime(DataSource dataSource, EventPublisher publisher, Clock clock,
                                    Consumer<RuntimeException> relayFailureHandler) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(clock, "clock");
        PostgresPaymentSagaRepository repository = new PostgresPaymentSagaRepository(dataSource);
        paymentService = new PaymentSagaService(repository);
        recoveryService = new PaymentRecoveryService(repository, clock, Duration.ofMinutes(5));
        TransactionalOutboxRelay relay = new TransactionalOutboxRelay(
                new PostgresOutboxStore(dataSource, "payment_outbox_events", EventTopic.PAYMENTS), publisher,
                UUID.randomUUID(), clock, Duration.ofSeconds(30), Duration.ofSeconds(5), 100);
        relayWorker = new OutboxRelayWorker("payment-outbox-relay", relay, Duration.ofMillis(500), relayFailureHandler);
    }

    public PaymentSagaService paymentService() { return paymentService; }
    public PaymentRecoveryService recoveryService() { return recoveryService; }
    @Override public void close() { relayWorker.close(); }
}
