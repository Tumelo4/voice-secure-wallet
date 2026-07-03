package com.voicesecure.launch;

import java.util.List;

public final class LaunchReadinessValidatorTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("fully signed-off launch plan passes", LaunchReadinessValidatorTests::fullySignedOffLaunchPlanPasses),
                new TestCase("missing chaos scenario and CVEs are blocked", LaunchReadinessValidatorTests::missingChaosScenarioAndCvesAreBlocked),
                new TestCase("shadow mode and fallback thresholds are enforced", LaunchReadinessValidatorTests::shadowModeAndFallbackThresholdsAreEnforced),
                new TestCase("benchmark evidence is required", LaunchReadinessValidatorTests::benchmarkEvidenceIsRequired),
                new TestCase("production cutover evidence is required", LaunchReadinessValidatorTests::productionCutoverEvidenceIsRequired)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Launch readiness validator tests passed: " + tests.length);
    }

    private static void fullySignedOffLaunchPlanPasses() {
        LaunchReadinessValidator validator = new LaunchReadinessValidator();
        LaunchReadinessReport report = validator.validate(validPlan());

        assertTrue(report.ready(), "valid launch plan should be ready");
        assertEquals(0, report.blockers().size(), "no blockers expected");
    }

    private static void missingChaosScenarioAndCvesAreBlocked() {
        LaunchReadinessValidator validator = new LaunchReadinessValidator();
        LaunchReadinessPlan plan = new LaunchReadinessPlan(
                true,
                new ChaosTestSuite(
                        List.of(
                                ChaosScenario.AWS_FIS_DEPENDENCY_FAULT,
                                ChaosScenario.AWS_FIS_LATENCY_SPIKE,
                                ChaosScenario.TOXIPROXY_VOICE_OUTAGE,
                                ChaosScenario.REGION_FAILOVER
                        ),
                        true
                ),
                true,
                new PenTestResult(true, 0, 0),
                true,
                true,
                true,
                new SecurityScanResult(true, 1, 0, true),
                true,
                new VoiceShadowModeValidation(48, 0.0005),
                new PerformanceTestResult(10, true),
                new VoiceFallbackExercise(100, 100, true),
                validEvidence(),
                true,
                true,
                true,
                true,
                validCutoverPlan()
        );

        LaunchReadinessReport report = validator.validate(plan);

        assertTrue(!report.ready(), "plan should be blocked");
        assertTrue(report.blockers().contains("all five chaos scenarios must be exercised"), "missing chaos scenario should be reported");
        assertTrue(report.blockers().contains("no Critical/High CVEs may remain in container images"), "CVE blocker should be reported");
    }

    private static void shadowModeAndFallbackThresholdsAreEnforced() {
        LaunchReadinessValidator validator = new LaunchReadinessValidator();
        LaunchReadinessPlan plan = new LaunchReadinessPlan(
                true,
                validChaosSuite(),
                true,
                new PenTestResult(true, 0, 0),
                true,
                true,
                true,
                new SecurityScanResult(true, 0, 0, true),
                true,
                new VoiceShadowModeValidation(24, 0.01),
                new PerformanceTestResult(10, true),
                new VoiceFallbackExercise(100, 99, true),
                validEvidence(),
                true,
                true,
                true,
                true,
                validCutoverPlan()
        );

        LaunchReadinessReport report = validator.validate(plan);

        assertTrue(!report.ready(), "plan should be blocked");
        assertTrue(report.blockers().contains("voice shadow mode must run for 48 hours"), "shadow duration should be enforced");
        assertTrue(report.blockers().contains("voice shadow mode false positive rate must stay below 0.1%"), "shadow false positive rate should be enforced");
        assertTrue(report.blockers().contains("voice OTP fallback must succeed for 100 of 100 test payments"), "fallback volume should be enforced");
    }

    private static void benchmarkEvidenceIsRequired() {
        LaunchReadinessPlan plan = new LaunchReadinessPlan(
                true,
                validChaosSuite(),
                true,
                new PenTestResult(true, 0, 0),
                true,
                true,
                true,
                new SecurityScanResult(true, 0, 0, true),
                true,
                new VoiceShadowModeValidation(48, 0.0008),
                new PerformanceTestResult(10, true),
                new VoiceFallbackExercise(100, 100, true),
                new LaunchEvidence("", 0, 1.0, 0, 0, 0, "", ""),
                true,
                true,
                true,
                true,
                validCutoverPlan()
        );

        LaunchReadinessReport report = new LaunchReadinessValidator().validate(plan);

        assertTrue(!report.ready(), "missing evidence should block launch");
        assertTrue(report.blockers().contains("benchmark evidence must include a test run id"), "test run evidence should be required");
        assertTrue(report.blockers().contains("benchmark evidence must include measured p99 latency"), "p99 evidence should be required");
        assertTrue(report.blockers().contains("benchmark evidence must include CVE scan source"), "scan evidence should be required");
    }

    private static void productionCutoverEvidenceIsRequired() {
        LaunchReadinessPlan plan = new LaunchReadinessPlan(
                true,
                validChaosSuite(),
                true,
                new PenTestResult(true, 0, 0),
                true,
                true,
                true,
                new SecurityScanResult(true, 0, 0, true),
                true,
                new VoiceShadowModeValidation(48, 0.0008),
                new PerformanceTestResult(10, true),
                new VoiceFallbackExercise(100, 100, true),
                validEvidence(),
                true,
                true,
                true,
                true,
                new ProductionCutoverPlan("", false, false, false, false, false, 45)
        );

        LaunchReadinessReport report = new LaunchReadinessValidator().validate(plan);

        assertTrue(!report.ready(), "missing production cutover evidence should block launch");
        assertTrue(report.blockers().contains("production change ticket must be linked"), "change ticket should be required");
        assertTrue(report.blockers().contains("rollback plan must be tested before cutover"), "rollback test should be required");
        assertTrue(report.blockers().contains("production monitoring and alerts must be armed"), "monitoring should be required");
        assertTrue(report.blockers().contains("rollback must be executable within 30 minutes"), "rollback timing should be enforced");
    }

    private static LaunchReadinessPlan validPlan() {
        return new LaunchReadinessPlan(
                true,
                validChaosSuite(),
                true,
                new PenTestResult(true, 0, 0),
                true,
                true,
                true,
                new SecurityScanResult(true, 0, 0, true),
                true,
                new VoiceShadowModeValidation(48, 0.0008),
                new PerformanceTestResult(10, true),
                new VoiceFallbackExercise(100, 100, true),
                validEvidence(),
                true,
                true,
                true,
                true,
                validCutoverPlan()
        );
    }

    private static ChaosTestSuite validChaosSuite() {
        return new ChaosTestSuite(
                List.of(
                        ChaosScenario.AWS_FIS_DEPENDENCY_FAULT,
                        ChaosScenario.AWS_FIS_LATENCY_SPIKE,
                        ChaosScenario.TOXIPROXY_VOICE_OUTAGE,
                        ChaosScenario.TOXIPROXY_KAFKA_PARTITION,
                        ChaosScenario.REGION_FAILOVER
                ),
                true
        );
    }

    private static LaunchEvidence validEvidence() {
        return new LaunchEvidence(
                "launch-drill-2026-06-18",
                220,
                10.0,
                100_000,
                15,
                5,
                "trivy-container-scan-2026-06-18",
                "pentest-report-2026-q2"
        );
    }

    private static ProductionCutoverPlan validCutoverPlan() {
        return new ProductionCutoverPlan(
                "CHG-2026-06-18-VOICESECURE",
                true,
                true,
                true,
                true,
                true,
                20
        );
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}
