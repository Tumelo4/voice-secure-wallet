package com.voicesecure.launch;

import java.util.Objects;

public record ProductionCutoverPlan(
        String changeTicketId,
        boolean rollbackPlanTested,
        boolean featureFlagsLocked,
        boolean productionMonitoringArmed,
        boolean onCallConfirmed,
        boolean supportBriefed,
        int rollbackTimeMinutes
) {
    public ProductionCutoverPlan {
        Objects.requireNonNull(changeTicketId, "changeTicketId");
    }
}
