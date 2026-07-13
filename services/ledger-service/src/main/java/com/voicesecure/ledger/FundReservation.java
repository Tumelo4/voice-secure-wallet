package com.voicesecure.ledger;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FundReservation(
        UUID reservationId, UUID paymentId, UUID accountId, long amount, String currency,
        Status status, Instant createdAt, Instant expiresAt
) {
    public enum Status { ACTIVE, CONSUMED, RELEASED, EXPIRED }

    public FundReservation {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(accountId, "accountId");
        currency = Objects.requireNonNull(currency, "currency").trim().toUpperCase(java.util.Locale.ROOT);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (amount <= 0) throw new LedgerException("reservation amount must be positive");
        if (!expiresAt.isAfter(createdAt)) throw new LedgerException("reservation expiry must be after creation");
    }
}
