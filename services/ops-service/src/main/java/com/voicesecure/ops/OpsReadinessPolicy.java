package com.voicesecure.ops;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record OpsReadinessPolicy(
        List<String> requiredLogFields,
        int requiredGoldenSignalCount,
        Set<String> requiredDashboardServices,
        List<DeploymentStage> requiredStages,
        int reconciliationScheduleHours
) {
    public OpsReadinessPolicy {
        Objects.requireNonNull(requiredLogFields, "requiredLogFields");
        Objects.requireNonNull(requiredDashboardServices, "requiredDashboardServices");
        Objects.requireNonNull(requiredStages, "requiredStages");
        requiredLogFields = List.copyOf(requiredLogFields);
        requiredDashboardServices = Set.copyOf(requiredDashboardServices);
        requiredStages = List.copyOf(requiredStages);
        if (requiredLogFields.isEmpty()) {
            throw new OpsException("required log fields cannot be empty");
        }
        if (requiredGoldenSignalCount <= 0) {
            throw new OpsException("required golden signal count must be positive");
        }
        if (requiredDashboardServices.isEmpty()) {
            throw new OpsException("required dashboard services cannot be empty");
        }
        if (requiredStages.isEmpty()) {
            throw new OpsException("required stages cannot be empty");
        }
        if (reconciliationScheduleHours <= 0) {
            throw new OpsException("reconciliation schedule hours must be positive");
        }
    }

    public static OpsReadinessPolicy defaults() {
        return new OpsReadinessPolicy(
                List.of("timestamp", "level", "service", "trace_id", "message"),
                4,
                Set.of("ledger-service", "payment-service", "identity-service", "support-service"),
                List.of(
                        DeploymentStage.BUILD_TEST,
                        DeploymentStage.CONTAINER_BUILD,
                        DeploymentStage.INTEGRATION_TESTS,
                        DeploymentStage.DEPLOY_STAGING,
                        DeploymentStage.DEPLOY_PRODUCTION
                ),
                6
        );
    }
}
