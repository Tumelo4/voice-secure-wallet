package com.voicesecure.ops;

import java.util.Objects;

public record SloDashboardSpec(
        String serviceName,
        int errorBudgetHours,
        double burnRateAlertMultiplier
) {
    public SloDashboardSpec {
        Objects.requireNonNull(serviceName, "serviceName");
        if (serviceName.isBlank()) {
            throw new OpsException("dashboard service name cannot be blank");
        }
    }
}
