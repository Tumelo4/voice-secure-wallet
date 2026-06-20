package com.voicesecure.ledger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryLedgerRepository implements LedgerRepository {
    private final List<LedgerEntry> entries = new ArrayList<>();
    private final List<OutboxEvent> outboxEvents = new ArrayList<>();
    private final Map<UUID, AccountState> accounts = new LinkedHashMap<>();
    private final Map<UUID, IdempotencyRecord> idempotencyCache = new HashMap<>();
    private final List<RepairRequest> repairAudit = new ArrayList<>();

    @Override
    public synchronized void createAccount(UUID accountId, String currency, long openingBalance) {
        if (accounts.containsKey(accountId)) {
            throw new LedgerException("account already exists");
        }
        if (openingBalance < 0) {
            throw new LedgerException("opening balance cannot be negative");
        }
        accounts.put(accountId, new AccountState(openingBalance, currency, 0, Instant.now()));
    }

    @Override
    public synchronized LedgerBatch append(LedgerTransaction transaction) {
        LedgerCommand command = LedgerCommand.from(transaction);
        IdempotencyRecord cached = idempotencyCache.get(transaction.idempotencyKey());
        if (cached != null) {
            cached.ensureMatches(command);
            return cached.batch();
        }
        return appendNew(transaction, command);
    }

    @Override
    public synchronized LedgerBatch appendRepair(RepairRequest repairRequest) {
        LedgerCommand command = LedgerCommand.from(repairRequest);
        IdempotencyRecord cached = idempotencyCache.get(repairRequest.idempotencyKey());
        if (cached != null) {
            cached.ensureMatches(command);
            return cached.batch();
        }
        LedgerTransaction transaction = new LedgerTransaction(
                repairRequest.sagaId(),
                repairRequest.idempotencyKey(),
                repairRequest.currency(),
                repairRequest.postings()
        );
        LedgerBatch batch = appendNew(transaction, command);
        repairAudit.add(repairRequest);
        return batch;
    }

    private LedgerBatch appendNew(LedgerTransaction transaction, LedgerCommand command) {
        validateAccounts(transaction);
        Map<UUID, Long> projectedBalances = projectBalances(transaction);
        List<LedgerEntry> batchEntries = createEntries(transaction);
        List<OutboxEvent> batchEvents = createOutboxEvents(transaction, batchEntries);

        projectedBalances.forEach((accountId, balance) -> {
            AccountState current = accounts.get(accountId);
            accounts.put(accountId, new AccountState(balance, current.currency, current.version + 1, Instant.now()));
        });

        entries.addAll(batchEntries);
        outboxEvents.addAll(batchEvents);

        ReconciliationReport reconciliation = ReconciliationReport.from(entries);
        if (!reconciliation.balanced()) {
            throw new LedgerException("ledger reconciliation failed after append");
        }

        LedgerBatch batch = new LedgerBatch(batchEntries, batchEvents, reconciliation);
        idempotencyCache.put(transaction.idempotencyKey(), new IdempotencyRecord(command, batch));
        return batch;
    }

    @Override
    public synchronized Optional<LedgerBatch> findByIdempotencyKey(UUID idempotencyKey) {
        return Optional.ofNullable(idempotencyCache.get(idempotencyKey)).map(IdempotencyRecord::batch);
    }

    @Override
    public synchronized List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized List<OutboxEvent> outboxEvents() {
        return List.copyOf(outboxEvents);
    }

    @Override
    public synchronized Map<UUID, AccountBalance> balances() {
        Map<UUID, AccountBalance> snapshot = new LinkedHashMap<>();
        accounts.forEach((accountId, state) -> snapshot.put(
                accountId,
                new AccountBalance(accountId, state.balance, state.currency, state.version, state.updatedAt)
        ));
        return Map.copyOf(snapshot);
    }

    public synchronized List<RepairRequest> repairAudit() {
        return List.copyOf(repairAudit);
    }

    private void validateAccounts(LedgerTransaction transaction) {
        for (Posting posting : transaction.postings()) {
            AccountState account = accounts.get(posting.accountId());
            if (account == null) {
                throw new LedgerException("account does not exist: " + posting.accountId());
            }
            if (!account.currency.equals(transaction.currency())) {
                throw new LedgerException("posting currency does not match account currency");
            }
        }
    }

    private Map<UUID, Long> projectBalances(LedgerTransaction transaction) {
        Map<UUID, Long> projected = new HashMap<>();
        for (Posting posting : transaction.postings()) {
            long current = projected.getOrDefault(posting.accountId(), accounts.get(posting.accountId()).balance);
            long next = current + posting.signedAmount();
            if (next < 0) {
                throw new LedgerException("insufficient funds for account: " + posting.accountId());
            }
            projected.put(posting.accountId(), next);
        }
        return projected;
    }

    private List<LedgerEntry> createEntries(LedgerTransaction transaction) {
        Instant now = Instant.now();
        List<LedgerEntry> batchEntries = new ArrayList<>();
        for (Posting posting : transaction.postings()) {
            batchEntries.add(new LedgerEntry(
                    UUID.randomUUID(),
                    posting.accountId(),
                    posting.signedAmount(),
                    transaction.currency(),
                    transaction.sagaId(),
                    posting.entryType(),
                    transaction.idempotencyKey(),
                    now
            ));
        }
        return List.copyOf(batchEntries);
    }

    private List<OutboxEvent> createOutboxEvents(LedgerTransaction transaction, List<LedgerEntry> batchEntries) {
        String payload = "{\"entryCount\":" + batchEntries.size() + ",\"currency\":\"" + transaction.currency() + "\"}";
        return List.of(new OutboxEvent(
                UUID.randomUUID(),
                "ledger.entry_posted",
                transaction.sagaId(),
                Instant.now(),
                payload
        ));
    }

    private record AccountState(long balance, String currency, long version, Instant updatedAt) {
    }

    private record IdempotencyRecord(LedgerCommand command, LedgerBatch batch) {
        private void ensureMatches(LedgerCommand candidate) {
            if (!command.equals(candidate)) {
                throw new LedgerException("idempotency key reused with different ledger command");
            }
        }
    }

    private record LedgerCommand(
            String kind,
            UUID sagaId,
            String currency,
            List<Posting> postings,
            UUID repairId,
            String justification,
            String requestedBy
    ) {
        private LedgerCommand {
            postings = List.copyOf(postings);
        }

        private static LedgerCommand from(LedgerTransaction transaction) {
            return new LedgerCommand(
                    "transaction",
                    transaction.sagaId(),
                    transaction.currency(),
                    transaction.postings(),
                    null,
                    "",
                    ""
            );
        }

        private static LedgerCommand from(RepairRequest repairRequest) {
            return new LedgerCommand(
                    "repair",
                    repairRequest.sagaId(),
                    repairRequest.currency(),
                    repairRequest.postings(),
                    repairRequest.repairId(),
                    repairRequest.justification(),
                    repairRequest.requestedBy()
            );
        }
    }
}
