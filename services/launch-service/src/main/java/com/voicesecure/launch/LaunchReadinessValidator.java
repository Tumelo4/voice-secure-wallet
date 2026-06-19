package com.voicesecure.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LaunchReadinessValidator {
    private static final Set<ChaosScenario> REQUIRED_SCENARIOS = Set.of(
            ChaosScenario.AWS_FIS_DEPENDENCY_FAULT,
            ChaosScenario.AWS_FIS_LATENCY_SPIKE,
            ChaosScenario.TOXIPROXY_VOICE_OUTAGE,
            ChaosScenario.TOXIPROXY_KAFKA_PARTITION,
            ChaosScenario.REGION_FAILOVER
    );

    public LaunchReadinessReport validate(LaunchReadinessPlan plan) {
        List<String> blockers = new ArrayList<>();
        if (!plan.reconciliationRunPassed()) {
            blockers.add("48-hour reconciliation run must pass");
        }
        validateChaosSuite(plan.chaosTestSuite(), blockers);
        if (!plan.drRestoreAndRepairPassed()) {
            blockers.add("DR restore and repair gate must pass");
        }
        validatePenTest(plan.penTestResult(), blockers);
        if (!plan.sloGreenFor48Hours()) {
            blockers.add("SLOs must be green for 48 hours");
        }
        if (!plan.runbooksWritten()) {
            blockers.add("all Tier 1 runbooks must be written");
        }
        if (!plan.contractTestsPassed()) {
            blockers.add("contract tests must pass");
        }
        validateSecurityScan(plan.securityScanResult(), blockers);
        if (!plan.migrationComplete()) {
            blockers.add("historical migration must be complete");
        }
        validateShadowMode(plan.voiceShadowModeValidation(), blockers);
        validatePerformance(plan.performanceTestResult(), blockers);
        validateVoiceFallbackExercise(plan.voiceFallbackExercise(), blockers);
        if (!plan.adminRepairDrillPassed()) {
            blockers.add("admin repair drill must pass");
        }
        if (!plan.deviceBindingPassed()) {
            blockers.add("device binding must pass");
        }
        if (!plan.complianceAuditPassed()) {
            blockers.add("compliance audit must pass");
        }
        if (!plan.documentationComplete()) {
            blockers.add("documentation must be complete");
        }
        return new LaunchReadinessReport(blockers.isEmpty(), blockers);
    }

    private void validateChaosSuite(ChaosTestSuite suite, List<String> blockers) {
        if (!Set.copyOf(suite.scenarios()).equals(REQUIRED_SCENARIOS)) {
            blockers.add("all five chaos scenarios must be exercised");
        }
        if (!suite.voiceFallbackCompletesPayment()) {
            blockers.add("voice outage chaos test must prove OTP fallback completes payment");
        }
    }

    private void validatePenTest(PenTestResult penTestResult, List<String> blockers) {
        if (!penTestResult.executed()) {
            blockers.add("penetration test must be executed");
        }
        if (penTestResult.criticalFindings() != 0 || penTestResult.highFindings() != 0) {
            blockers.add("penetration test must have zero Critical/High findings");
        }
    }

    private void validateSecurityScan(SecurityScanResult securityScanResult, List<String> blockers) {
        if (!securityScanResult.owaspZapClean()) {
            blockers.add("OWASP ZAP must be clean in CI");
        }
        if (securityScanResult.criticalCveCount() != 0 || securityScanResult.highCveCount() != 0) {
            blockers.add("no Critical/High CVEs may remain in container images");
        }
        if (!securityScanResult.noSecretsDetected()) {
            blockers.add("no secrets may be detected in code, env, or images");
        }
    }

    private void validateShadowMode(VoiceShadowModeValidation shadowModeValidation, List<String> blockers) {
        if (shadowModeValidation.hours() < 48) {
            blockers.add("voice shadow mode must run for 48 hours");
        }
        if (shadowModeValidation.falsePositiveRate() >= 0.001) {
            blockers.add("voice shadow mode false positive rate must stay below 0.1%");
        }
    }

    private void validatePerformance(PerformanceTestResult performanceTestResult, List<String> blockers) {
        if (performanceTestResult.loadMultiplier() < 10) {
            blockers.add("performance test must reach 10x load");
        }
        if (!performanceTestResult.p99LatencyWithinSlo()) {
            blockers.add("p99 latency must stay within SLO under load");
        }
    }

    private void validateVoiceFallbackExercise(VoiceFallbackExercise voiceFallbackExercise, List<String> blockers) {
        if (!voiceFallbackExercise.voiceDegraded()) {
            blockers.add("voice fallback exercise must intentionally degrade voice");
        }
        if (voiceFallbackExercise.attemptedPayments() != 100 || voiceFallbackExercise.successfulPayments() != 100) {
            blockers.add("voice OTP fallback must succeed for 100 of 100 test payments");
        }
    }
}
