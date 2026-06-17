package com.voicesecure.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class LedgerServiceTests {
    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("double-entry transfer keeps signed ledger balanced", LedgerServiceTests::transferBalancesLedger),
                new TestCase("zero and unbalanced postings are rejected", LedgerServiceTests::rejectsInvalidPostings),
                new TestCase("idempotency returns the original accepted batch", LedgerServiceTests::idempotencyPreventsDuplicates),
                new TestCase("concurrent transfers cannot overdraft", LedgerServiceTests::concurrentTransfersCannotOverdraft),
                new TestCase("repair requires justification and appends audit-backed entries", LedgerServiceTests::repairFlowIsBalancedAndAudited)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Ledger service tests passed: " + tests.length);
    }

    private static void transferBalancesLedger() {
        Fixture fixture = fixture(1_000, 0);

        LedgerBatch batch = fixture.service.transfer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                fixture.source,
                fixture.destination,
                250,
                "ZAR"
        );

        assertEquals(2, batch.entries().size(), "transfer should create debit and credit entries");
        assertEquals(0L, fixture.service.reconcile().totalSignedAmount(), "ledger must stay zero-sum");
        assertEquals(750L, fixture.repository.balances().get(fixture.source).balance(), "source balance");
        assertEquals(250L, fixture.repository.balances().get(fixture.destination).balance(), "destination balance");
        assertEquals(1, fixture.repository.outboxEvents().size(), "outbox event count");
    }

    private static void rejectsInvalidPostings() {
        Fixture fixture = fixture(100, 0);

        assertThrows(
                LedgerException.class,
                () -> fixture.service.transfer(UUID.randomUUID(), UUID.randomUUID(), fixture.source, fixture.destination, 0, "ZAR"),
                "zero transfer should fail"
        );

        assertThrows(
                LedgerException.class,
                () -> new LedgerTransaction(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "ZAR",
                        List.of(Posting.credit(fixture.destination, 50), Posting.credit(fixture.source, 50))
                ),
                "unbalanced transaction should fail"
        );
    }

    private static void idempotencyPreventsDuplicates() {
        Fixture fixture = fixture(1_000, 0);
        UUID idempotencyKey = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();

        LedgerBatch first = fixture.service.transfer(sagaId, idempotencyKey, fixture.source, fixture.destination, 125, "ZAR");
        LedgerBatch second = fixture.service.transfer(sagaId, idempotencyKey, fixture.source, fixture.destination, 125, "ZAR");

        assertSame(first, second, "idempotent retry should return cached batch");
        assertEquals(2, fixture.repository.entries().size(), "retry must not append duplicate entries");
        assertEquals(875L, fixture.repository.balances().get(fixture.source).balance(), "source balance after retry");
        assertEquals(125L, fixture.repository.balances().get(fixture.destination).balance(), "destination balance after retry");
    }

    private static void concurrentTransfersCannotOverdraft() throws Exception {
        Fixture fixture = fixture(1_000, 0);
        int attempts = 10;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                await(start);
                try {
                    fixture.service.transfer(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            fixture.source,
                            fixture.destination,
                            200,
                            "ZAR"
                    );
                    successes.incrementAndGet();
                } catch (LedgerException ignored) {
                    // Expected for attempts that would overdraw the source account.
                }
            });
            threads.add(thread);
            thread.start();
        }

        ready.await();
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(5, successes.get(), "only funded transfers should succeed");
        assertEquals(0L, fixture.repository.balances().get(fixture.source).balance(), "source should be exactly depleted");
        assertEquals(1_000L, fixture.repository.balances().get(fixture.destination).balance(), "destination should receive funded transfers");
        assertEquals(0L, fixture.service.reconcile().totalSignedAmount(), "ledger must stay balanced after concurrency");
    }

    private static void repairFlowIsBalancedAndAudited() {
        Fixture fixture = fixture(1_000, 0);
        fixture.service.transfer(UUID.randomUUID(), UUID.randomUUID(), fixture.source, fixture.destination, 400, "ZAR");

        assertThrows(
                LedgerException.class,
                () -> new RepairRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "ZAR",
                        List.of(Posting.repairDebit(fixture.destination, 50), Posting.repairCredit(fixture.source, 50)),
                        "too short",
                        "sre@example.com"
                ),
                "short repair justification should fail"
        );

        RepairRequest repair = new RepairRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZAR",
                List.of(Posting.repairDebit(fixture.destination, 50), Posting.repairCredit(fixture.source, 50)),
                "COMPENSATION_FAILED drill corrective entry",
                "sre@example.com"
        );

        LedgerBatch batch = fixture.service.repair(repair);
        LedgerBatch retry = fixture.service.repair(repair);

        assertEquals(2, batch.entries().size(), "repair should append balanced entries");
        assertSame(batch, retry, "repair retry should return cached batch");
        assertEquals(4, fixture.repository.entries().size(), "repair must append without mutating history");
        assertEquals(650L, fixture.repository.balances().get(fixture.source).balance(), "source after repair");
        assertEquals(350L, fixture.repository.balances().get(fixture.destination).balance(), "destination after repair");
        assertEquals(1, fixture.repository.repairAudit().size(), "repair audit should be recorded");
        assertEquals(0L, fixture.service.reconcile().totalSignedAmount(), "repair must preserve zero-sum ledger");
    }

    private static Fixture fixture(long sourceOpeningBalance, long destinationOpeningBalance) {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        LedgerService service = new LedgerService(repository);
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        service.createAccount(source, "ZAR", sourceOpeningBalance);
        service.createAccount(destination, "ZAR", destinationOpeningBalance);
        return new Fixture(service, repository, source, destination);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", ex);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got " + actual, actual);
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    private record Fixture(
            LedgerService service,
            InMemoryLedgerRepository repository,
            UUID source,
            UUID destination
    ) {
    }

    private record TestCase(String name, ThrowingRunnable runnable) {
        void run() throws Exception {
            runnable.run();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
