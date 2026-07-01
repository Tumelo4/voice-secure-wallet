package com.voicesecure.wallet;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventTopic;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class WalletService {
    private static final String LEDGER_ENTRY_POSTED = "ledger.entry_posted";

    private final WalletRepository repository;

    public WalletService(WalletRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public WalletAccount openWallet(UUID userId, UUID accountId, String displayName, String currency) {
        WalletAccount account = new WalletAccount(userId, accountId, displayName, currency, Instant.now());
        repository.saveAccount(account);
        repository.saveBalance(new WalletBalance(accountId, account.currency(), 0, 0, account.openedAt()));
        return account;
    }

    public WalletBalance projectLedgerEntry(EventEnvelope envelope) {
        requireLedgerEntry(envelope);
        if (repository.hasProcessedEvent(envelope.eventId())) {
            return balance(UUID.fromString(WalletJson.stringField(envelope.payload(), "accountId")));
        }

        UUID accountId = UUID.fromString(WalletJson.stringField(envelope.payload(), "accountId"));
        long signedAmount = WalletJson.longField(envelope.payload(), "signedAmount");
        String currency = WalletJson.stringField(envelope.payload(), "currency");
        WalletAccount account = repository.findAccount(accountId)
                .orElseThrow(() -> new WalletException("wallet account not found: " + accountId));
        if (!account.currency().equals(currency)) {
            throw new WalletException("ledger currency does not match wallet currency");
        }

        WalletBalance current = repository.findBalance(accountId)
                .orElse(new WalletBalance(accountId, account.currency(), 0, 0, account.openedAt()));
        WalletBalance next = new WalletBalance(
                accountId,
                account.currency(),
                current.balance() + signedAmount,
                current.version() + 1,
                envelope.occurredAt()
        );
        repository.saveBalance(next);
        repository.markProcessedEvent(envelope.eventId());
        return next;
    }

    public WalletBalance balance(UUID accountId) {
        return repository.findBalance(accountId)
                .orElseThrow(() -> new WalletException("wallet balance not found: " + accountId));
    }

    public List<WalletAccount> accountsForUser(UUID userId) {
        return repository.accountsForUser(userId);
    }

    private void requireLedgerEntry(EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!envelope.isForTopic(EventTopic.LEDGER)) {
            throw new WalletException("wallet service only consumes ledger events");
        }
        if (!LEDGER_ENTRY_POSTED.equals(envelope.eventType())) {
            throw new WalletException("wallet service only projects ledger entry events");
        }
    }
}
