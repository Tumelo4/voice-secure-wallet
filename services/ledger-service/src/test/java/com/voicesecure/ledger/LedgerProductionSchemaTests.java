package com.voicesecure.ledger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LedgerProductionSchemaTests {
    private static final Path MIGRATION = Path.of("services", "ledger-service", "src", "main", "resources", "db", "migration", "V002__ledger_production.sql");
    private static final Path OUTBOX_MIGRATION = Path.of("services", "ledger-service", "src", "main", "resources", "db", "migration", "V006__outbox_delivery_leases.sql");

    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("ledger production migration declares batch table", LedgerProductionSchemaTests::declaresBatchTable),
                new TestCase("ledger production migration hardens idempotency and append-only constraints", LedgerProductionSchemaTests::hardensIdempotencyAndAppendOnlyConstraints),
                new TestCase("ledger outbox supports durable leased delivery", LedgerProductionSchemaTests::declaresDurableOutbox)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Ledger production schema tests passed: " + tests.length);
    }

    private static void declaresDurableOutbox() throws IOException {
        String migration = Files.readString(OUTBOX_MIGRATION).replaceAll("\\s+", " ");
        assertContains(migration, "lease_owner UUID", "delivery lease owner");
        assertContains(migration, "lease_until TIMESTAMPTZ", "delivery lease expiry");
        assertContains(migration, "next_attempt_at TIMESTAMPTZ", "retry schedule");
    }

    private static void declaresBatchTable() throws IOException {
        String migration = readMigration();
        assertContains(migration, "CREATE TABLE ledger_batches", "ledger batch table");
        assertContains(migration, "batch_kind TEXT NOT NULL", "batch kind");
        assertContains(migration, "command_hash TEXT NOT NULL", "command hash");
    }

    private static void hardensIdempotencyAndAppendOnlyConstraints() throws IOException {
        String migration = readMigration();
        assertContains(migration, "UNIQUE (batch_id, entry_position)", "entry position uniqueness");
        assertContains(migration, "UNIQUE (idempotency_key)", "idempotency uniqueness");
        assertContains(migration, "ledger_entries_balance_check", "balance trigger");
        assertContains(migration, "publish_attempts INTEGER NOT NULL DEFAULT 0", "outbox retries");
        assertContains(migration, "last_error TEXT", "outbox error tracking");
    }

    private static String readMigration() throws IOException {
        return Files.readString(MIGRATION).replaceAll("\\s+", " ");
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + ": expected to find " + expected);
        }
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
