package com.voicesecure.ledger;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventTopic;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
        UUID id,
        UUID accountId,
        long signedAmount,
        String currency,
        UUID sagaId,
        EntryType entryType,
        UUID idempotencyKey,
        Instant createdAt
) {
    public LedgerEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(createdAt, "createdAt");
        if (signedAmount == 0) {
            throw new LedgerException("signed_amount cannot be zero");
        }
        if (signedAmount < 0 && entryType != EntryType.DEBIT && entryType != EntryType.REPAIR_DEBIT) {
            throw new LedgerException("negative signed_amount must use a debit entry type");
        }
        if (signedAmount > 0 && entryType != EntryType.CREDIT && entryType != EntryType.REPAIR_CREDIT) {
            throw new LedgerException("positive signed_amount must use a credit entry type");
        }
    }

    public EventEnvelope toEnvelope(String traceId) {
        return EventEnvelopeFactory.create(
                EventTopic.LEDGER,
                accountId,
                "LedgerEntry",
                "ledger.entry_posted",
                createdAt,
                traceId,
                payloadJson()
        );
    }

    private String payloadJson() {
        return "{"
                + "\"accountId\":\"" + escape(accountId.toString()) + "\","
                + "\"signedAmount\":" + signedAmount + ","
                + "\"currency\":\"" + escape(currency) + "\","
                + "\"sagaId\":\"" + escape(sagaId.toString()) + "\","
                + "\"entryType\":\"" + escape(entryType.name()) + "\","
                + "\"idempotencyKey\":\"" + escape(idempotencyKey.toString()) + "\""
                + "}";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
