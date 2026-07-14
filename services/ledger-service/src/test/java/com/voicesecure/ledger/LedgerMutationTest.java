package com.voicesecure.ledger;

import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.infrastructure.InMemoryLedgerRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LedgerMutationTest {
    @Test
    void transferIsBalancedIdempotentAndCannotOverdraw() {
        Fixture fixture = fixture();
        UUID saga = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        LedgerBatch first = fixture.service.transfer(saga, key, fixture.source, fixture.destination, 250, "ZAR");
        LedgerBatch retry = fixture.service.transfer(saga, key, fixture.source, fixture.destination, 250, "ZAR");
        assertNotNull(first);
        assertSame(first, retry);
        assertEquals(2, fixture.repository.entries().size());
        assertEquals(750, fixture.repository.balances().get(fixture.source).balance());
        assertEquals(250, fixture.repository.balances().get(fixture.destination).balance());
        assertEquals(0, fixture.service.reconcile().totalSignedAmount());
        assertThrows(LedgerException.class, () -> fixture.service.transfer(
                UUID.randomUUID(), UUID.randomUUID(), fixture.source, fixture.destination, 751, "ZAR"));
    }

    @Test
    void reservationReducesAvailableFundsAndReleaseRestoresThem() {
        Fixture fixture = fixture();
        UUID reservationId = UUID.randomUUID();
        FundReservation reservation = fixture.service.reserveFunds(
                reservationId, UUID.randomUUID(), fixture.source, 400, "ZAR", Duration.ofMinutes(15));
        assertEquals(FundReservation.Status.ACTIVE, reservation.status());
        assertEquals(600, fixture.repository.balances().get(fixture.source).availableBalance());
        assertThrows(LedgerException.class, () -> fixture.service.transfer(
                UUID.randomUUID(), UUID.randomUUID(), fixture.source, fixture.destination, 700, "ZAR"));
        assertEquals(FundReservation.Status.RELEASED, fixture.service.releaseFunds(reservationId).status());
        assertEquals(1_000, fixture.repository.balances().get(fixture.source).availableBalance());
    }

    @Test
    void dualControlledRepairReturnsTheAppendedBatch() {
        Fixture fixture = fixture();
        RepairRequest request = new RepairRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ZAR",
                java.util.List.of(Posting.repairDebit(fixture.source, 25), Posting.repairCredit(fixture.destination, 25)),
                "Correct compensation after provider mismatch", "maker@example.com", "checker@example.com");
        LedgerBatch batch = fixture.service.repair(request);
        assertNotNull(batch);
        assertEquals(2, batch.entries().size());
        assertEquals(1, fixture.repository.repairAudit().size());
    }

    private static Fixture fixture() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        LedgerService service = new LedgerService(repository);
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        service.createAccount(source, "ZAR", 1_000);
        service.createAccount(destination, "ZAR", 0);
        return new Fixture(service, repository, source, destination);
    }

    private record Fixture(LedgerService service, InMemoryLedgerRepository repository,
                           UUID source, UUID destination) { }
}
