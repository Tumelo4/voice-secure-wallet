package com.voicesecure.ledger;

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

public final class LedgerProductionRuntime implements AutoCloseable {
    private final LedgerService ledgerService;
    private final OutboxRelayWorker relayWorker;

    public LedgerProductionRuntime(DataSource dataSource, EventPublisher publisher, Clock clock,
                                   Consumer<RuntimeException> relayFailureHandler) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(clock, "clock");
        ledgerService = new LedgerService(new PostgresLedgerRepository(dataSource));
        TransactionalOutboxRelay relay = new TransactionalOutboxRelay(
                new PostgresOutboxStore(dataSource, "outbox_events", EventTopic.LEDGER), publisher,
                UUID.randomUUID(), clock, Duration.ofSeconds(30), Duration.ofSeconds(5), 100);
        relayWorker = new OutboxRelayWorker("ledger-outbox-relay", relay, Duration.ofMillis(500), relayFailureHandler);
    }

    public LedgerService ledgerService() { return ledgerService; }
    @Override public void close() { relayWorker.close(); }
}
