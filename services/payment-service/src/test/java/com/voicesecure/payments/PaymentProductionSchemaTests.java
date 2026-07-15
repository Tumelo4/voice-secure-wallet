package com.voicesecure.payments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PaymentProductionSchemaTests {
    private static final Path MIGRATION = Path.of("services", "payment-service", "src", "main", "resources", "db", "migration", "V001__payment_saga.sql");
    private static final Path OUTBOX_MIGRATION = Path.of("services", "payment-service", "src", "main", "resources", "db", "migration", "V005__transactional_outbox.sql");

    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("payment production migration declares saga table", PaymentProductionSchemaTests::declaresSagaTable),
                new TestCase("payment production migration declares event log and state constraints", PaymentProductionSchemaTests::declaresEventLogAndConstraints),
                new TestCase("payment outbox supports durable leased delivery", PaymentProductionSchemaTests::declaresDurableOutbox)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Payment production schema tests passed: " + tests.length);
    }

    private static void declaresDurableOutbox() throws IOException {
        String migration = Files.readString(OUTBOX_MIGRATION).replaceAll("\\s+", " ");
        assertContains(migration, "CREATE TABLE payment_outbox_events", "payment outbox table");
        assertContains(migration, "publish_attempts INTEGER NOT NULL DEFAULT 0", "attempt counter");
        assertContains(migration, "lease_owner UUID", "delivery lease owner");
        assertContains(migration, "next_attempt_at TIMESTAMPTZ", "retry schedule");
    }

    private static void declaresSagaTable() throws IOException {
        String migration = readMigration();
        assertContains(migration, "CREATE TABLE payment_sagas", "payment saga table");
        assertContains(migration, "persisted_event_count BIGINT NOT NULL DEFAULT 0", "event count");
        assertContains(migration, "state_history TEXT NOT NULL", "state history");
    }

    private static void declaresEventLogAndConstraints() throws IOException {
        String migration = readMigration();
        assertContains(migration, "CREATE TABLE payment_saga_events", "payment saga event table");
        assertContains(migration, "payment_saga_state_known", "state constraint");
        assertContains(migration, "payment_saga_auth_policy_known", "auth policy constraint");
        assertContains(migration, "payment_saga_fallback_method_known", "fallback constraint");
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
