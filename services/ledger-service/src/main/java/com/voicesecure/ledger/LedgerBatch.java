package com.voicesecure.ledger;

import java.util.List;

public record LedgerBatch(
        List<LedgerEntry> entries,
        List<OutboxEvent> outboxEvents,
        ReconciliationReport reconciliation
) {
    public LedgerBatch {
        entries = List.copyOf(entries);
        outboxEvents = List.copyOf(outboxEvents);
        if (!reconciliation.balanced()) {
            throw new LedgerException("ledger batch cannot be returned with failed reconciliation");
        }
    }
}

