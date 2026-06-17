package com.voicesecure.ledger;

import java.util.List;

public record ReconciliationReport(long totalSignedAmount, int entryCount) {
    public boolean balanced() {
        return totalSignedAmount == 0;
    }

    public static ReconciliationReport from(List<LedgerEntry> entries) {
        long total = entries.stream().mapToLong(LedgerEntry::signedAmount).sum();
        return new ReconciliationReport(total, entries.size());
    }
}

