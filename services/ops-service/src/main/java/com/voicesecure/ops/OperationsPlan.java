package com.voicesecure.ops;

import java.util.List;
import java.util.Objects;

public record OperationsPlan(
        String systemName,
        List<ServiceTelemetrySpec> telemetrySpecs,
        List<SloDashboardSpec> dashboards,
        List<AlertSpec> alerts,
        DeploymentPipelineSpec pipeline,
        DisasterRecoverySpec disasterRecovery,
        int reconciliationScheduleHours
) {
    public OperationsPlan {
        Objects.requireNonNull(systemName, "systemName");
        Objects.requireNonNull(telemetrySpecs, "telemetrySpecs");
        Objects.requireNonNull(dashboards, "dashboards");
        Objects.requireNonNull(alerts, "alerts");
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(disasterRecovery, "disasterRecovery");
        telemetrySpecs = List.copyOf(telemetrySpecs);
        dashboards = List.copyOf(dashboards);
        alerts = List.copyOf(alerts);
        if (systemName.isBlank()) {
            throw new OpsException("system name cannot be blank");
        }
        if (reconciliationScheduleHours <= 0) {
            throw new OpsException("reconciliation schedule hours must be positive");
        }
    }
}
