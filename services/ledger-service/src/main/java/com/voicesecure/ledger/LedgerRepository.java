package com.voicesecure.ledger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LedgerRepository {
    void createAccount(UUID accountId, String currency, long openingBalance);

    LedgerBatch append(LedgerTransaction transaction);

    LedgerBatch appendRepair(RepairRequest repairRequest);

    Optional<LedgerBatch> findByIdempotencyKey(UUID idempotencyKey);

    List<LedgerEntry> entries();

    List<OutboxEvent> outboxEvents();

    Map<UUID, AccountBalance> balances();
}
