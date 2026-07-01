package com.voicesecure.wallet;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventTopic;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WalletServiceTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("wallet projects ledger events into account balances", WalletServiceTests::projectsLedgerEventsIntoBalances),
                new TestCase("wallet projection is idempotent by event id", WalletServiceTests::projectionIsIdempotentByEventId),
                new TestCase("wallet rejects non-ledger events", WalletServiceTests::rejectsNonLedgerEvents),
                new TestCase("wallet lists user accounts without exposing ledger writes", WalletServiceTests::listsUserAccounts)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Wallet service tests passed: " + tests.length);
    }

    private static void projectsLedgerEventsIntoBalances() {
        WalletService service = new WalletService(new InMemoryWalletRepository());
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        service.openWallet(userId, accountId, "Everyday wallet", "ZAR");

        service.projectLedgerEntry(ledgerEvent(accountId, 1000, "ZAR", "trace-wallet-1"));
        service.projectLedgerEntry(ledgerEvent(accountId, -250, "ZAR", "trace-wallet-2"));

        WalletBalance balance = service.balance(accountId);
        assertEquals(750L, balance.balance(), "balance should reflect signed ledger events");
        assertEquals(2L, balance.version(), "balance version");
        assertEquals("ZAR", balance.currency(), "currency");
    }

    private static void projectionIsIdempotentByEventId() {
        WalletService service = new WalletService(new InMemoryWalletRepository());
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        service.openWallet(userId, accountId, "Savings wallet", "ZAR");
        EventEnvelope event = ledgerEvent(accountId, 500, "ZAR", "trace-wallet-3");

        service.projectLedgerEntry(event);
        service.projectLedgerEntry(event);

        WalletBalance balance = service.balance(accountId);
        assertEquals(500L, balance.balance(), "duplicate ledger event should not double count");
        assertEquals(1L, balance.version(), "duplicate ledger event should not increment version");
    }

    private static void rejectsNonLedgerEvents() {
        WalletService service = new WalletService(new InMemoryWalletRepository());
        EventEnvelope paymentEvent = EventEnvelopeFactory.create(
                EventTopic.PAYMENTS,
                UUID.randomUUID(),
                "Payment",
                "payment.completed",
                Instant.parse("2026-06-20T12:00:00Z"),
                "trace-payment",
                "{\"status\":\"COMPLETED\"}"
        );

        assertThrows(WalletException.class, () -> service.projectLedgerEntry(paymentEvent), "non-ledger event should fail");
    }

    private static void listsUserAccounts() {
        WalletService service = new WalletService(new InMemoryWalletRepository());
        UUID userId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        service.openWallet(userId, first, "Everyday wallet", "ZAR");
        service.openWallet(userId, second, "Savings wallet", "ZAR");

        List<WalletAccount> accounts = service.accountsForUser(userId);

        assertEquals(2, accounts.size(), "user account count");
        assertTrue(accounts.stream().anyMatch(account -> account.accountId().equals(first)), "first account listed");
        assertTrue(accounts.stream().anyMatch(account -> account.accountId().equals(second)), "second account listed");
    }

    private static EventEnvelope ledgerEvent(UUID accountId, long signedAmount, String currency, String traceId) {
        String payload = "{"
                + "\"accountId\":\"" + accountId + "\","
                + "\"signedAmount\":" + signedAmount + ","
                + "\"currency\":\"" + currency + "\","
                + "\"sagaId\":\"" + UUID.randomUUID() + "\","
                + "\"entryType\":\"" + (signedAmount < 0 ? "DEBIT" : "CREDIT") + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\""
                + "}";
        return EventEnvelopeFactory.create(
                EventTopic.LEDGER,
                accountId,
                "LedgerEntry",
                "ledger.entry_posted",
                Instant.parse("2026-06-20T12:00:00Z"),
                traceId,
                payload
        );
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(Class<? extends RuntimeException> expected, Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            if (expected.isInstance(ex)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got " + ex.getClass().getSimpleName(), ex);
        }
        throw new AssertionError(message + ": expected exception " + expected.getSimpleName());
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}
