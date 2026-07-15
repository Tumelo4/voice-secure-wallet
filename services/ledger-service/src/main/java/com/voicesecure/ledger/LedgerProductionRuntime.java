package com.voicesecure.ledger;

import com.voicesecure.events.EventPublisher;
import com.voicesecure.events.EventTopic;
import com.voicesecure.events.OutboxRelayWorker;
import com.voicesecure.events.OutboxRelayTelemetry;
import com.voicesecure.events.OutboxRetryPolicy;
import com.voicesecure.events.PostgresOutboxStore;
import com.voicesecure.events.TransactionalOutboxRelay;
import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.infrastructure.PostgresLedgerRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;

public final class LedgerProductionRuntime implements AutoCloseable {
    private final LedgerService ledgerService;
    private final OutboxRelayWorker relayWorker;
    private final OutboxRelayTelemetry relayTelemetry;

    public LedgerProductionRuntime(DataSource dataSource, EventPublisher publisher, Clock clock,
                                   Consumer<RuntimeException> relayFailureHandler) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(clock, "clock");
        ledgerService = new LedgerService(new PostgresLedgerRepository(dataSource));
        relayTelemetry = new OutboxRelayTelemetry();
        TransactionalOutboxRelay relay = new TransactionalOutboxRelay(
                new PostgresOutboxStore(dataSource, "outbox_events", EventTopic.LEDGER), publisher,
                UUID.randomUUID(), clock, Duration.ofSeconds(30),
                new OutboxRetryPolicy(Duration.ofSeconds(5), Duration.ofMinutes(5), 8, 0.2),
                100, relayTelemetry);
        relayWorker = new OutboxRelayWorker("ledger-outbox-relay", relay, Duration.ofMillis(500), relayFailureHandler);
    }

    public LedgerService ledgerService() { return ledgerService; }
    public OutboxRelayTelemetry.Snapshot relayTelemetry() { return relayTelemetry.snapshot(); }
    @Override public void close() { relayWorker.close(); }
}
