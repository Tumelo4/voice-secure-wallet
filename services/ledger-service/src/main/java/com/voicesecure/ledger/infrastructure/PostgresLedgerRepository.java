package com.voicesecure.ledger.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicesecure.ledger.*;
import com.voicesecure.ledger.domain.LedgerRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresLedgerRepository implements LedgerRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SELECT_BATCH = """
            SELECT idempotency_key, saga_id, currency, batch_kind, repair_id, justification, requested_by, command_hash, created_at
            FROM ledger_batches
            WHERE idempotency_key = ?
            """;
    private static final String INSERT_BATCH = """
            INSERT INTO ledger_batches (
                idempotency_key,
                saga_id,
                currency,
                batch_kind,
                repair_id,
                justification,
                requested_by,
                command_hash,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """;
    private static final String SELECT_ACCOUNTS = """
            SELECT account_id, balance, reserved_balance, currency, version, updated_at
            FROM account_balances
            WHERE account_id = ?
            FOR UPDATE
            """;
    private static final String INSERT_ACCOUNT = """
            INSERT INTO accounts (id, currency, created_at)
            VALUES (?, ?, ?)
            """;
    private static final String INSERT_BALANCE = """
            INSERT INTO account_balances (account_id, balance, currency, version, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_BALANCE = """
            UPDATE account_balances
            SET balance = ?, version = ?, updated_at = ?
            WHERE account_id = ?
            """;
    private static final String INSERT_ENTRY = """
            INSERT INTO ledger_entries (
                id,
                batch_id,
                entry_position,
                account_id,
                signed_amount,
                currency,
                saga_id,
                entry_type,
                idempotency_key,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_ENTRIES = """
            SELECT id, account_id, signed_amount, currency, saga_id, entry_type, idempotency_key, created_at
            FROM ledger_entries
            WHERE batch_id = ?
            ORDER BY entry_position, created_at, id
            """;
    private static final String INSERT_OUTBOX = """
            INSERT INTO outbox_events (
                id,
                batch_id,
                aggregate_id,
                aggregate_type,
                event_type,
                event_version,
                payload,
                trace_id,
                created_at,
                published_at,
                publish_attempts,
                last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0, NULL)
            """;
    private static final String SELECT_OUTBOX = """
            SELECT id, event_type, aggregate_id, created_at, payload
            FROM outbox_events
            WHERE batch_id = ?
            ORDER BY created_at, id
            """;
    private static final String INSERT_REPAIR_AUDIT = """
            INSERT INTO ledger_repair_audit (
                id,
                repair_id,
                saga_id,
                idempotency_key,
                requested_by,
                approved_by,
                justification,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BALANCES = """
            SELECT account_id, balance, reserved_balance, currency, version, updated_at
            FROM account_balances
            ORDER BY account_id
            """;
    private static final String SELECT_RESERVATION = """
            SELECT reservation_id, payment_id, account_id, amount, currency, status, created_at, expires_at
            FROM fund_reservations WHERE reservation_id = ?
            """;
    private static final String SELECT_RESERVATION_FOR_UPDATE = SELECT_RESERVATION + " FOR UPDATE";
    private static final String INSERT_RESERVATION = """
            INSERT INTO fund_reservations (
                reservation_id, payment_id, account_id, amount, currency, status, created_at, expires_at
            ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """;
    private static final String UPDATE_RESERVED_BALANCE = """
            UPDATE account_balances SET reserved_balance = ?, version = ?, updated_at = ? WHERE account_id = ?
            """;
    private static final String UPDATE_RESERVATION_STATUS = """
            UPDATE fund_reservations SET status = ? WHERE reservation_id = ?
            """;

    private final DataSource dataSource;

    public PostgresLedgerRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void createAccount(UUID accountId, String currency, long openingBalance) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        if (openingBalance < 0) {
            throw new LedgerException("opening balance cannot be negative");
        }
        inTransaction(connection -> {
            Instant now = Instant.now();
            try (PreparedStatement account = connection.prepareStatement(INSERT_ACCOUNT);
                 PreparedStatement balance = connection.prepareStatement(INSERT_BALANCE)) {
                account.setObject(1, accountId);
                account.setString(2, currency);
                account.setTimestamp(3, Timestamp.from(now));
                account.executeUpdate();

                balance.setObject(1, accountId);
                balance.setLong(2, openingBalance);
                balance.setString(3, currency);
                balance.setLong(4, 0L);
                balance.setTimestamp(5, Timestamp.from(now));
                balance.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public LedgerBatch append(LedgerTransaction transaction) {
        return append(transaction, null);
    }

    @Override
    public LedgerBatch consumeReservation(UUID reservationId, LedgerTransaction transaction) {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(transaction, "transaction");
        if (expireReservationIfNecessary(reservationId, Instant.now())) {
            throw new LedgerException("reservation expired");
        }
        LedgerCommand command = LedgerCommand.from(transaction, null);
        String commandHash = hash(command);
        try {
            return inTransaction(connection -> {
                Optional<LoadedBatch> existing = loadBatch(connection, transaction.idempotencyKey());
                if (existing.isPresent()) {
                    existing.get().ensureMatches(command, commandHash);
                    return existing.get().batch();
                }
                FundReservation reservation = loadReservation(connection, reservationId, true)
                        .orElseThrow(() -> new LedgerException("reservation not found"));
                requireMatchingReservation(reservation, transaction);
                if (reservation.status() == FundReservation.Status.CONSUMED) {
                    LoadedBatch completed = loadBatch(connection, transaction.idempotencyKey())
                            .orElseThrow(() -> new LedgerException("consumed reservation has no ledger batch"));
                    completed.ensureMatches(command, commandHash);
                    return completed.batch();
                }
                if (reservation.status() != FundReservation.Status.ACTIVE) {
                    throw new LedgerException("reservation is not active");
                }
                AccountState source = lockAccount(connection, reservation.accountId());
                Instant now = Instant.now();
                try (PreparedStatement update = connection.prepareStatement(UPDATE_RESERVATION_STATUS)) {
                    update.setString(1, FundReservation.Status.CONSUMED.name());
                    update.setObject(2, reservationId);
                    update.executeUpdate();
                }
                updateReservedBalance(connection, reservation.accountId(),
                        source.reserved() - reservation.amount(), source.version() + 1, now);
                return appendWithinTransaction(connection, transaction, null, command, commandHash);
            });
        } catch (LedgerException failure) {
            if (isUniqueViolation(failure.getCause())) return append(transaction, null);
            throw failure;
        }
    }

    @Override
    public LedgerBatch appendRepair(RepairRequest repairRequest) {
        LedgerTransaction transaction = new LedgerTransaction(
                repairRequest.sagaId(),
                repairRequest.idempotencyKey(),
                repairRequest.currency(),
                repairRequest.postings()
        );
        return append(transaction, repairRequest);
    }

    @Override
    public Optional<LedgerBatch> findByIdempotencyKey(UUID idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return inTransaction(connection -> loadBatch(connection, idempotencyKey).map(LoadedBatch::batch));
    }

    @Override
    public FundReservation reserve(
            UUID reservationId, UUID paymentId, UUID accountId, long amount, String currency, Duration ttl
    ) {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(accountId, "accountId");
        if (amount <= 0 || ttl == null || ttl.isZero() || ttl.isNegative()) throw new LedgerException("invalid reservation");
        return inTransaction(connection -> {
            Optional<FundReservation> existing = loadReservation(connection, reservationId, false);
            if (existing.isPresent()) {
                FundReservation value = existing.get();
                if (!value.paymentId().equals(paymentId) || !value.accountId().equals(accountId)
                        || value.amount() != amount || !value.currency().equals(currency)) {
                    throw new LedgerException("reservation id reused with different request");
                }
                return value;
            }
            AccountState account = lockAccount(connection, accountId);
            if (!account.currency().equals(currency)) throw new LedgerException("reservation currency does not match account currency");
            if (account.balance() - account.reserved() < amount) throw new LedgerException("insufficient available funds");
            Instant now = Instant.now();
            FundReservation reservation = new FundReservation(
                    reservationId, paymentId, accountId, amount, currency,
                    FundReservation.Status.ACTIVE, now, now.plus(ttl));
            try (PreparedStatement insert = connection.prepareStatement(INSERT_RESERVATION)) {
                insert.setObject(1, reservationId); insert.setObject(2, paymentId); insert.setObject(3, accountId);
                insert.setLong(4, amount); insert.setString(5, currency);
                insert.setTimestamp(6, Timestamp.from(now)); insert.setTimestamp(7, Timestamp.from(reservation.expiresAt()));
                insert.executeUpdate();
            }
            updateReservedBalance(connection, accountId, account.reserved() + amount, account.version() + 1, now);
            return reservation;
        });
    }

    @Override
    public FundReservation release(UUID reservationId) {
        return inTransaction(connection -> {
            FundReservation current = loadReservation(connection, reservationId, true)
                    .orElseThrow(() -> new LedgerException("reservation not found"));
            if (current.status() != FundReservation.Status.ACTIVE) return current;
            AccountState account = lockAccount(connection, current.accountId());
            Instant now = Instant.now();
            try (PreparedStatement update = connection.prepareStatement(UPDATE_RESERVATION_STATUS)) {
                update.setString(1, FundReservation.Status.RELEASED.name());
                update.setObject(2, reservationId);
                update.executeUpdate();
            }
            updateReservedBalance(connection, current.accountId(), account.reserved() - current.amount(), account.version() + 1, now);
            return new FundReservation(
                    current.reservationId(), current.paymentId(), current.accountId(), current.amount(), current.currency(),
                    FundReservation.Status.RELEASED, current.createdAt(), current.expiresAt());
        });
    }

    @Override
    public Optional<FundReservation> findReservation(UUID reservationId) {
        return inTransaction(connection -> loadReservation(connection, reservationId, false));
    }

    private boolean expireReservationIfNecessary(UUID reservationId, Instant now) {
        return inTransaction(connection -> {
            FundReservation current = loadReservation(connection, reservationId, true)
                    .orElseThrow(() -> new LedgerException("reservation not found"));
            if (current.status() != FundReservation.Status.ACTIVE || current.expiresAt().isAfter(now)) {
                return false;
            }
            AccountState account = lockAccount(connection, current.accountId());
            try (PreparedStatement update = connection.prepareStatement(UPDATE_RESERVATION_STATUS)) {
                update.setString(1, FundReservation.Status.EXPIRED.name());
                update.setObject(2, reservationId);
                update.executeUpdate();
            }
            updateReservedBalance(connection, current.accountId(),
                    account.reserved() - current.amount(), account.version() + 1, now);
            return true;
        });
    }

    private static void requireMatchingReservation(FundReservation reservation, LedgerTransaction transaction) {
        Posting debit = transaction.postings().stream().filter(posting -> posting.signedAmount() < 0).findFirst()
                .orElseThrow(() -> new LedgerException("reserved transfer requires a debit"));
        if (!reservation.paymentId().equals(transaction.sagaId())
                || !reservation.accountId().equals(debit.accountId())
                || reservation.amount() != -debit.signedAmount()
                || !reservation.currency().equals(transaction.currency())) {
            throw new LedgerException("reservation does not match ledger transaction");
        }
    }

    @Override
    public List<LedgerEntry> entries() {
        return inTransaction(connection -> {
            List<LedgerEntry> items = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, account_id, signed_amount, currency, saga_id, entry_type, idempotency_key, created_at
                    FROM ledger_entries
                    ORDER BY created_at, id
                    """)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(readEntry(resultSet));
                    }
                }
            }
            return List.copyOf(items);
        });
    }

    @Override
    public List<OutboxEvent> outboxEvents() {
        return inTransaction(connection -> {
            List<OutboxEvent> items = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, event_type, aggregate_id, created_at, payload
                    FROM outbox_events
                    ORDER BY created_at, id
                    """)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(new OutboxEvent(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("event_type"),
                                resultSet.getObject("aggregate_id", UUID.class),
                                resultSet.getTimestamp("created_at").toInstant(),
                                resultSet.getString("payload")
                        ));
                    }
                }
            }
            return List.copyOf(items);
        });
    }

    @Override
    public Map<UUID, AccountBalance> balances() {
        return inTransaction(connection -> {
            Map<UUID, AccountBalance> snapshot = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_BALANCES);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    AccountBalance balance = new AccountBalance(
                            resultSet.getObject("account_id", UUID.class),
                            resultSet.getLong("balance"),
                            resultSet.getLong("balance") - resultSet.getLong("reserved_balance"),
                            resultSet.getLong("reserved_balance"),
                            0,
                            resultSet.getString("currency"),
                            resultSet.getLong("version"),
                            resultSet.getTimestamp("updated_at").toInstant()
                    );
                    snapshot.put(balance.accountId(), balance);
                }
            }
            return Map.copyOf(snapshot);
        });
    }

    private LedgerBatch append(LedgerTransaction transaction, RepairRequest repairRequest) {
        LedgerCommand command = LedgerCommand.from(transaction, repairRequest);
        String commandHash = hash(command);
        try {
            return inTransaction(connection -> appendWithinTransaction(connection, transaction, repairRequest, command, commandHash));
        } catch (LedgerException ex) {
            if (isUniqueViolation(ex.getCause())) {
                return inTransaction(connection -> {
                    LoadedBatch loaded = loadBatch(connection, transaction.idempotencyKey())
                            .orElseThrow(() -> new LedgerException("duplicate idempotency key could not be resolved"));
                    loaded.ensureMatches(command, commandHash);
                    return loaded.batch();
                });
            }
            throw ex;
        }
    }

    private LedgerBatch appendWithinTransaction(
            Connection connection,
            LedgerTransaction transaction,
            RepairRequest repairRequest,
            LedgerCommand command,
            String commandHash
    ) throws SQLException {
        Optional<LoadedBatch> existing = loadBatch(connection, transaction.idempotencyKey());
        if (existing.isPresent()) {
            LoadedBatch loaded = existing.get();
            loaded.ensureMatches(command, commandHash);
            return loaded.batch();
        }

        Map<UUID, AccountState> accounts = lockAccounts(connection, transaction);
        Map<UUID, Long> projectedBalances = projectBalances(transaction, accounts);
        List<LedgerEntry> batchEntries = createEntries(transaction);
        List<OutboxEvent> batchEvents = createOutboxEvents(transaction, batchEntries);
        Instant now = Instant.now();

        try (PreparedStatement batchStatement = connection.prepareStatement(INSERT_BATCH)) {
            bindBatch(batchStatement, transaction, repairRequest, commandHash, now);
            int inserted = batchStatement.executeUpdate();
            if (inserted == 0) {
                LoadedBatch loaded = loadBatch(connection, transaction.idempotencyKey())
                        .orElseThrow(() -> new LedgerException("duplicate batch was not visible after conflict"));
                loaded.ensureMatches(command, commandHash);
                return loaded.batch();
            }
        }

        for (Map.Entry<UUID, Long> entry : projectedBalances.entrySet()) {
            AccountState current = accounts.get(entry.getKey());
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_BALANCE)) {
                statement.setLong(1, entry.getValue());
                statement.setLong(2, current.version() + 1);
                statement.setTimestamp(3, Timestamp.from(now));
                statement.setObject(4, entry.getKey());
                statement.executeUpdate();
            }
        }

        insertEntries(connection, transaction, batchEntries);
        insertOutbox(connection, transaction, batchEvents);
        if (repairRequest != null) {
            insertRepairAudit(connection, repairRequest, now);
        }

        ReconciliationReport reconciliation = ReconciliationReport.from(batchEntries);
        if (!reconciliation.balanced()) {
            throw new LedgerException("ledger reconciliation failed after append");
        }
        return new LedgerBatch(batchEntries, batchEvents, reconciliation);
    }

    private Optional<LoadedBatch> loadBatch(Connection connection, UUID idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BATCH)) {
            statement.setObject(1, idempotencyKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                BatchRecord record = new BatchRecord(
                        resultSet.getObject("idempotency_key", UUID.class),
                        resultSet.getObject("saga_id", UUID.class),
                        resultSet.getString("currency"),
                        resultSet.getString("batch_kind"),
                        resultSet.getObject("repair_id", UUID.class),
                        resultSet.getString("justification"),
                        resultSet.getString("requested_by"),
                        resultSet.getString("command_hash"),
                        resultSet.getTimestamp("created_at").toInstant()
                );
                List<LedgerEntry> entries = loadEntries(connection, record.batchId());
                List<OutboxEvent> outboxEvents = loadOutboxEvents(connection, record.batchId());
                LedgerBatch batch = new LedgerBatch(entries, outboxEvents, ReconciliationReport.from(entries));
                return Optional.of(new LoadedBatch(record, batch));
            }
        }
    }

    private List<LedgerEntry> loadEntries(Connection connection, UUID batchId) throws SQLException {
        List<LedgerEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ENTRIES)) {
            statement.setObject(1, batchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(readEntry(resultSet));
                }
            }
        }
        return List.copyOf(entries);
    }

    private List<OutboxEvent> loadOutboxEvents(Connection connection, UUID batchId) throws SQLException {
        List<OutboxEvent> events = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_OUTBOX)) {
            statement.setObject(1, batchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(new OutboxEvent(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("event_type"),
                            resultSet.getObject("aggregate_id", UUID.class),
                            resultSet.getTimestamp("created_at").toInstant(),
                            resultSet.getString("payload")
                    ));
                }
            }
        }
        return List.copyOf(events);
    }

    private Map<UUID, AccountState> lockAccounts(Connection connection, LedgerTransaction transaction) throws SQLException {
        Map<UUID, AccountState> accounts = new LinkedHashMap<>();
        List<UUID> accountIds = transaction.postings().stream()
                .map(Posting::accountId)
                .distinct()
                .sorted()
                .toList();
        for (UUID accountId : accountIds) {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_ACCOUNTS)) {
                statement.setObject(1, accountId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new LedgerException("account does not exist: " + accountId);
                    }
                    AccountState state = new AccountState(
                            resultSet.getLong("balance"),
                            resultSet.getLong("reserved_balance"),
                            resultSet.getString("currency"),
                            resultSet.getLong("version"),
                            resultSet.getTimestamp("updated_at").toInstant()
                    );
                    accounts.put(accountId, state);
                }
            }
        }
        return accounts;
    }

    private Map<UUID, Long> projectBalances(LedgerTransaction transaction, Map<UUID, AccountState> accounts) {
        Map<UUID, Long> projected = new LinkedHashMap<>();
        for (Posting posting : transaction.postings()) {
            AccountState current = accounts.get(posting.accountId());
            if (current == null) {
                throw new LedgerException("account does not exist: " + posting.accountId());
            }
            if (!current.currency().equals(transaction.currency())) {
                throw new LedgerException("posting currency does not match account currency");
            }
            long currentBalance = projected.getOrDefault(posting.accountId(), current.balance());
            long next = currentBalance + posting.signedAmount();
            if (next < current.reserved()) {
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
        String payload = payloadJson(transaction, batchEntries);
        return List.of(new OutboxEvent(
                UUID.randomUUID(),
                "ledger.entry_posted",
                transaction.idempotencyKey(),
                Instant.now(),
                payload
        ));
    }

    private void insertEntries(Connection connection, LedgerTransaction transaction, List<LedgerEntry> batchEntries) throws SQLException {
        for (int i = 0; i < batchEntries.size(); i++) {
            LedgerEntry entry = batchEntries.get(i);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_ENTRY)) {
                statement.setObject(1, entry.id());
                statement.setObject(2, transaction.idempotencyKey());
                statement.setInt(3, i + 1);
                statement.setObject(4, entry.accountId());
                statement.setLong(5, entry.signedAmount());
                statement.setString(6, entry.currency());
                statement.setObject(7, entry.sagaId());
                statement.setString(8, entry.entryType().name());
                statement.setObject(9, entry.idempotencyKey());
                statement.setTimestamp(10, Timestamp.from(entry.createdAt()));
                statement.executeUpdate();
            }
        }
    }

    private void insertOutbox(Connection connection, LedgerTransaction transaction, List<OutboxEvent> batchEvents) throws SQLException {
        for (OutboxEvent event : batchEvents) {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_OUTBOX)) {
                statement.setObject(1, event.id());
                statement.setObject(2, transaction.idempotencyKey());
                statement.setObject(3, transaction.idempotencyKey());
                statement.setString(4, "LedgerBatch");
                statement.setString(5, event.eventType());
                statement.setString(6, "1.0");
                statement.setString(7, event.payload());
                statement.setString(8, transaction.sagaId().toString());
                statement.setTimestamp(9, Timestamp.from(event.occurredAt()));
                statement.executeUpdate();
            }
        }
    }

    private void insertRepairAudit(Connection connection, RepairRequest repairRequest, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_REPAIR_AUDIT)) {
            statement.setObject(1, repairRequest.repairId());
            statement.setObject(2, repairRequest.repairId());
            statement.setObject(3, repairRequest.sagaId());
            statement.setObject(4, repairRequest.idempotencyKey());
            statement.setString(5, repairRequest.requestedBy());
            statement.setString(6, repairRequest.approvedBy());
            statement.setString(7, repairRequest.justification());
            statement.setTimestamp(8, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private void bindBatch(PreparedStatement statement, LedgerTransaction transaction, RepairRequest repairRequest, String commandHash, Instant now) throws SQLException {
        statement.setObject(1, transaction.idempotencyKey());
        statement.setObject(2, transaction.sagaId());
        statement.setString(3, transaction.currency());
        statement.setString(4, repairRequest == null ? "TRANSFER" : "REPAIR");
        if (repairRequest == null) {
            statement.setNull(5, java.sql.Types.OTHER);
            statement.setNull(6, java.sql.Types.VARCHAR);
            statement.setNull(7, java.sql.Types.VARCHAR);
        } else {
            statement.setObject(5, repairRequest.repairId());
            statement.setString(6, repairRequest.justification());
            statement.setString(7, repairRequest.requestedBy());
        }
        statement.setString(8, commandHash);
        statement.setTimestamp(9, Timestamp.from(now));
    }

    private LedgerEntry readEntry(ResultSet resultSet) throws SQLException {
        return new LedgerEntry(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("account_id", UUID.class),
                resultSet.getLong("signed_amount"),
                resultSet.getString("currency"),
                resultSet.getObject("saga_id", UUID.class),
                EntryType.valueOf(resultSet.getString("entry_type")),
                resultSet.getObject("idempotency_key", UUID.class),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private static String payloadJson(LedgerTransaction transaction, List<LedgerEntry> batchEntries) {
        try {
            return JSON.writeValueAsString(Map.of(
                    "batchId", transaction.idempotencyKey().toString(),
                    "sagaId", transaction.sagaId().toString(),
                    "idempotencyKey", transaction.idempotencyKey().toString(),
                    "currency", transaction.currency(),
                    "entryCount", batchEntries.size()));
        } catch (JsonProcessingException exception) {
            throw new LedgerException("unable to serialize ledger outbox payload", exception);
        }
    }

    private static boolean isUniqueViolation(Throwable cause) {
        return cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState());
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw new LedgerException("database operation failed", ex);
            } catch (RuntimeException ex) {
                rollbackQuietly(connection);
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new LedgerException("database connection failed", ex);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort only.
        }
    }

    private String hash(LedgerCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(command.canonical().getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new LedgerException("SHA-256 digest unavailable", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >>> 4) & 0x0F, 16));
            builder.append(Character.forDigit(b & 0x0F, 16));
        }
        return builder.toString();
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private AccountState lockAccount(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ACCOUNTS)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new LedgerException("account does not exist: " + accountId);
                return new AccountState(
                        resultSet.getLong("balance"), resultSet.getLong("reserved_balance"),
                        resultSet.getString("currency"), resultSet.getLong("version"),
                        resultSet.getTimestamp("updated_at").toInstant());
            }
        }
    }

    private Optional<FundReservation> loadReservation(Connection connection, UUID reservationId, boolean forUpdate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(forUpdate ? SELECT_RESERVATION_FOR_UPDATE : SELECT_RESERVATION)) {
            statement.setObject(1, reservationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                return Optional.of(new FundReservation(
                        resultSet.getObject("reservation_id", UUID.class), resultSet.getObject("payment_id", UUID.class),
                        resultSet.getObject("account_id", UUID.class), resultSet.getLong("amount"),
                        resultSet.getString("currency"), FundReservation.Status.valueOf(resultSet.getString("status")),
                        resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("expires_at").toInstant()));
            }
        }
    }

    private void updateReservedBalance(Connection connection, UUID accountId, long reserved, long version, Instant now) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(UPDATE_RESERVED_BALANCE)) {
            update.setLong(1, reserved); update.setLong(2, version);
            update.setTimestamp(3, Timestamp.from(now)); update.setObject(4, accountId);
            update.executeUpdate();
        }
    }

    private record AccountState(long balance, long reserved, String currency, long version, Instant updatedAt) {
    }

    private record BatchRecord(
            UUID batchId,
            UUID sagaId,
            String currency,
            String batchKind,
            UUID repairId,
            String justification,
            String requestedBy,
            String commandHash,
            Instant createdAt
    ) {
    }

    private record LoadedBatch(BatchRecord record, LedgerBatch batch) {
        void ensureMatches(LedgerCommand command, String commandHash) {
            if (!record.commandHash().equals(commandHash)) {
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
            String requestedBy,
            String approvedBy
    ) {
        private LedgerCommand {
            postings = List.copyOf(postings);
        }

        static LedgerCommand from(LedgerTransaction transaction, RepairRequest repairRequest) {
            if (repairRequest == null) {
                return new LedgerCommand("TRANSFER", transaction.sagaId(), transaction.currency(), transaction.postings(), null, "", "", "");
            }
            return new LedgerCommand(
                    "REPAIR",
                    repairRequest.sagaId(),
                    repairRequest.currency(),
                    repairRequest.postings(),
                    repairRequest.repairId(),
                    repairRequest.justification(),
                    repairRequest.requestedBy(),
                    repairRequest.approvedBy()
            );
        }

        String canonical() {
            StringJoiner postingsJoiner = new StringJoiner(";");
            for (Posting posting : postings) {
                postingsJoiner.add(posting.accountId() + ":" + posting.signedAmount() + ":" + posting.entryType());
            }
            return kind
                    + "|" + sagaId
                    + "|" + currency
                    + "|" + repairId
                    + "|" + justification
                    + "|" + requestedBy
                    + "|" + approvedBy
                    + "|" + postingsJoiner;
        }
    }
}
