package com.voicesecure.ledger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

public interface LedgerRepository {
    void createAccount(UUID accountId, String currency, long openingBalance);

    LedgerBatch append(LedgerTransaction transaction);

    LedgerBatch appendRepair(RepairRequest repairRequest);

    FundReservation reserve(UUID reservationId, UUID paymentId, UUID accountId, long amount, String currency, Duration ttl);

    FundReservation release(UUID reservationId);

    Optional<FundReservation> findReservation(UUID reservationId);

    Optional<LedgerBatch> findByIdempotencyKey(UUID idempotencyKey);

    List<LedgerEntry> entries();

    List<OutboxEvent> outboxEvents();

    Map<UUID, AccountBalance> balances();
}
