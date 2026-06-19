package com.voicesecure.ops;

import java.util.Objects;

public record DisasterRecoverySpec(
        boolean crossRegionReplica,
        boolean ledgerRestoreTestPassed,
        boolean reconciliationRestoreTestPassed,
        boolean monthlyRestoreTestScheduled,
        boolean mtlsEnabled,
        boolean objectLockEnabled,
        boolean autoSuspendOnReconciliationFailure,
        String backupStrategy
) {
    public DisasterRecoverySpec {
        Objects.requireNonNull(backupStrategy, "backupStrategy");
        if (backupStrategy.isBlank()) {
            throw new OpsException("backup strategy cannot be blank");
        }
    }
}
