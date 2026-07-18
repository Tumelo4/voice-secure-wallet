package com.voicesecure.ledger;

import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.infrastructure.InMemoryLedgerRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LedgerCriticalTest {
    @Test
    void reservedTransferIsBalancedAndIdempotent() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        LedgerService ledger = new LedgerService(repository);
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        ledger.createAccount(source, "ZAR", 100_000L);
        ledger.createAccount(destination, "ZAR", 0L);
        ledger.reserveFunds(saga, saga, source, 25_000L, "ZAR", Duration.ofMinutes(15));

        LedgerBatch first = ledger.commitReservedTransfer(saga, saga, idempotencyKey, source, destination, 25_000L, "ZAR");
        LedgerBatch retry = ledger.commitReservedTransfer(saga, saga, idempotencyKey, source, destination, 25_000L, "ZAR");

        assertEquals(first.entries(), retry.entries());
        assertEquals(0L, ledger.reconcile().totalSignedAmount());
        assertEquals(75_000L, repository.balances().get(source).balance());
        assertEquals(25_000L, repository.balances().get(destination).balance());
        assertEquals(2, repository.entries().size());
    }

    @Test
    void activeReservationPreventsOverspend() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        LedgerService ledger = new LedgerService(repository);
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        ledger.createAccount(source, "ZAR", 100_000L);
        ledger.createAccount(destination, "ZAR", 0L);
        ledger.reserveFunds(UUID.randomUUID(), UUID.randomUUID(), source, 60_000L, "ZAR", Duration.ofMinutes(15));

        assertThrows(LedgerException.class, () -> ledger.transfer(
                UUID.randomUUID(), UUID.randomUUID(), source, destination, 50_000L, "ZAR"));
    }
}
