package com.voicesecure.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class OpsPlanValidator {
    private static final List<String> REQUIRED_LOG_FIELDS = List.of("timestamp", "level", "service", "trace_id", "message");
    private static final List<DeploymentStage> REQUIRED_STAGES = List.of(
            DeploymentStage.BUILD_TEST,
            DeploymentStage.CONTAINER_BUILD,
            DeploymentStage.INTEGRATION_TESTS,
            DeploymentStage.DEPLOY_STAGING,
            DeploymentStage.DEPLOY_PRODUCTION
    );

    public OpsPlanValidationReport validate(OperationsPlan plan) {
        List<String> blockers = new ArrayList<>();
        validateTelemetry(plan, blockers);
        validateDashboards(plan, blockers);
        validateAlerts(plan, blockers);
        validatePipeline(plan, blockers);
        validateDisasterRecovery(plan, blockers);
        validateReconciliationSchedule(plan, blockers);
        return new OpsPlanValidationReport(blockers.isEmpty(), blockers);
    }

    private void validateTelemetry(OperationsPlan plan, List<String> blockers) {
        if (plan.telemetrySpecs().isEmpty()) {
            blockers.add("at least one service telemetry spec is required");
            return;
        }
        for (ServiceTelemetrySpec spec : plan.telemetrySpecs()) {
            if (!spec.otelEnabled()) {
                blockers.add(spec.serviceName() + " must enable OpenTelemetry");
            }
            if (!"trace_id".equals(spec.traceHeader())) {
                blockers.add(spec.serviceName() + " must propagate the trace_id header");
            }
            if (spec.goldenSignals().size() != 4) {
                blockers.add(spec.serviceName() + " must define four golden signals");
            }
            if (!spec.structuredLogFields().containsAll(REQUIRED_LOG_FIELDS)) {
                blockers.add(spec.serviceName() + " must emit the required structured log fields");
            }
        }
    }

    private void validateDashboards(OperationsPlan plan, List<String> blockers) {
        if (plan.dashboards().size() != 4) {
            blockers.add("exactly four SLO dashboards are required");
            return;
        }
        for (SloDashboardSpec dashboard : plan.dashboards()) {
            if (dashboard.errorBudgetHours() <= 0) {
                blockers.add(dashboard.serviceName() + " must have a positive error budget");
            }
            if (dashboard.burnRateAlertMultiplier() <= 1.0) {
                blockers.add(dashboard.serviceName() + " must define a burn rate alert above 1.0");
            }
        }
    }

    private void validateAlerts(OperationsPlan plan, List<String> blockers) {
        Set<AlertTier> tiers = plan.alerts().stream().map(AlertSpec::tier).collect(java.util.stream.Collectors.toSet());
        if (!tiers.containsAll(Set.of(AlertTier.TIER_1, AlertTier.TIER_2, AlertTier.TIER_3))) {
            blockers.add("all three alert tiers must be modeled");
        }
        boolean tierOneHasRunbook = plan.alerts().stream()
                .filter(alert -> alert.tier() == AlertTier.TIER_1)
                .allMatch(alert -> !alert.runbookLink().isBlank());
        if (!tierOneHasRunbook) {
            blockers.add("every Tier 1 alert must link to a runbook");
        }
    }

    private void validatePipeline(OperationsPlan plan, List<String> blockers) {
        if (!plan.pipeline().stages().equals(REQUIRED_STAGES)) {
            blockers.add("the five-stage release pipeline must be modeled in order");
        }
        if (!plan.pipeline().manualApprovalRequired()) {
            blockers.add("production deploys require manual approval");
        }
        if (!plan.pipeline().blueGreenEnabled()) {
            blockers.add("blue/green release must be enabled");
        }
        if (!plan.pipeline().canaryEnabled()) {
            blockers.add("canary release must be enabled");
        }
    }

    private void validateDisasterRecovery(OperationsPlan plan, List<String> blockers) {
        DisasterRecoverySpec dr = plan.disasterRecovery();
        if (!dr.crossRegionReplica()) {
            blockers.add("cross-region replica must be enabled");
        }
        if (!dr.ledgerRestoreTestPassed()) {
            blockers.add("ledger restore test must pass");
        }
        if (!dr.reconciliationRestoreTestPassed()) {
            blockers.add("reconciliation restore test must pass");
        }
        if (!dr.monthlyRestoreTestScheduled()) {
            blockers.add("monthly DR restore test must be scheduled");
        }
        if (!dr.mtlsEnabled()) {
            blockers.add("mTLS must be enabled");
        }
        if (!dr.objectLockEnabled()) {
            blockers.add("S3 Object Lock WORM must be enabled");
        }
        if (!dr.autoSuspendOnReconciliationFailure()) {
            blockers.add("auto-suspend must be enabled for reconciliation failures");
        }
    }

    private void validateReconciliationSchedule(OperationsPlan plan, List<String> blockers) {
        if (plan.reconciliationScheduleHours() != 6) {
            blockers.add("reconciliation must run every 6 hours");
        }
    }
}
