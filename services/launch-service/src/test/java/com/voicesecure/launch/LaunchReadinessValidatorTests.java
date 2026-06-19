package com.voicesecure.launch;

import java.util.List;

public final class LaunchReadinessValidatorTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("fully signed-off launch plan passes", LaunchReadinessValidatorTests::fullySignedOffLaunchPlanPasses),
                new TestCase("missing chaos scenario and CVEs are blocked", LaunchReadinessValidatorTests::missingChaosScenarioAndCvesAreBlocked),
                new TestCase("shadow mode and fallback thresholds are enforced", LaunchReadinessValidatorTests::shadowModeAndFallbackThresholdsAreEnforced)
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
                true,
                true,
                true,
                true
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
                true,
                true,
                true,
                true
        );

        LaunchReadinessReport report = validator.validate(plan);

        assertTrue(!report.ready(), "plan should be blocked");
        assertTrue(report.blockers().contains("voice shadow mode must run for 48 hours"), "shadow duration should be enforced");
        assertTrue(report.blockers().contains("voice shadow mode false positive rate must stay below 0.1%"), "shadow false positive rate should be enforced");
        assertTrue(report.blockers().contains("voice OTP fallback must succeed for 100 of 100 test payments"), "fallback volume should be enforced");
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
                true,
                true,
                true,
                true
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
