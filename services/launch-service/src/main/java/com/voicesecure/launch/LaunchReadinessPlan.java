package com.voicesecure.launch;

import java.util.Objects;

public record LaunchReadinessPlan(
        boolean reconciliationRunPassed,
        ChaosTestSuite chaosTestSuite,
        boolean drRestoreAndRepairPassed,
        PenTestResult penTestResult,
        boolean sloGreenFor48Hours,
        boolean runbooksWritten,
        boolean contractTestsPassed,
        SecurityScanResult securityScanResult,
        boolean migrationComplete,
        VoiceShadowModeValidation voiceShadowModeValidation,
        PerformanceTestResult performanceTestResult,
        VoiceFallbackExercise voiceFallbackExercise,
        LaunchEvidence launchEvidence,
        boolean adminRepairDrillPassed,
        boolean deviceBindingPassed,
        boolean complianceAuditPassed,
        boolean documentationComplete,
        ProductionCutoverPlan productionCutoverPlan
) {
    public LaunchReadinessPlan {
        Objects.requireNonNull(chaosTestSuite, "chaosTestSuite");
        Objects.requireNonNull(penTestResult, "penTestResult");
        Objects.requireNonNull(securityScanResult, "securityScanResult");
        Objects.requireNonNull(voiceShadowModeValidation, "voiceShadowModeValidation");
        Objects.requireNonNull(performanceTestResult, "performanceTestResult");
        Objects.requireNonNull(voiceFallbackExercise, "voiceFallbackExercise");
        Objects.requireNonNull(launchEvidence, "launchEvidence");
        Objects.requireNonNull(productionCutoverPlan, "productionCutoverPlan");
    }
}
