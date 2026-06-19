package com.voicesecure.ops;

import java.util.List;

public final class OpsPlanValidatorTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("valid phase six plan passes readiness checks", OpsPlanValidatorTests::validPhaseSixPlanPassesReadinessChecks),
                new TestCase("missing DR restore coverage is blocked", OpsPlanValidatorTests::missingDrRestoreCoverageIsBlocked),
                new TestCase("telemetry must define four golden signals", OpsPlanValidatorTests::telemetryMustDefineFourGoldenSignals)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Ops plan validator tests passed: " + tests.length);
    }

    private static void validPhaseSixPlanPassesReadinessChecks() {
        OpsPlanValidator validator = new OpsPlanValidator();
        OpsPlanValidationReport report = validator.validate(validPlan());

        assertTrue(report.ready(), "valid plan should be ready");
        assertEquals(0, report.blockers().size(), "no blockers expected");
    }

    private static void missingDrRestoreCoverageIsBlocked() {
        OpsPlanValidator validator = new OpsPlanValidator();
        OperationsPlan plan = new OperationsPlan(
                "VoiceSecure Wallet",
                telemetrySpecs(),
                dashboardSpecs(),
                alertSpecs(),
                pipelineSpec(),
                new DisasterRecoverySpec(true, false, true, true, true, true, true, "RDS automated snapshots"),
                6
        );

        OpsPlanValidationReport report = validator.validate(plan);

        assertTrue(!report.ready(), "plan should be blocked");
        assertTrue(report.blockers().contains("ledger restore test must pass"), "missing restore test should be reported");
    }

    private static void telemetryMustDefineFourGoldenSignals() {
        OpsPlanValidator validator = new OpsPlanValidator();
        List<ServiceTelemetrySpec> telemetrySpecs = List.of(
                new ServiceTelemetrySpec(
                        "ledger-service",
                        List.of("latency", "traffic", "errors"),
                        requiredLogFields(),
                        "trace_id",
                        true
                ),
                new ServiceTelemetrySpec(
                        "payment-service",
                        List.of("latency", "traffic", "errors", "saturation"),
                        requiredLogFields(),
                        "trace_id",
                        true
                )
        );
        OperationsPlan plan = new OperationsPlan(
                "VoiceSecure Wallet",
                telemetrySpecs,
                dashboardSpecs(),
                alertSpecs(),
                pipelineSpec(),
                drSpec(),
                6
        );

        OpsPlanValidationReport report = validator.validate(plan);

        assertTrue(!report.ready(), "plan should be blocked");
        assertTrue(report.blockers().contains("ledger-service must define four golden signals"), "telemetry blocker should be reported");
    }

    private static OperationsPlan validPlan() {
        return new OperationsPlan(
                "VoiceSecure Wallet",
                telemetrySpecs(),
                dashboardSpecs(),
                alertSpecs(),
                pipelineSpec(),
                drSpec(),
                6
        );
    }

    private static List<ServiceTelemetrySpec> telemetrySpecs() {
        return List.of(
                new ServiceTelemetrySpec("ledger-service", goldenSignals(), requiredLogFields(), "trace_id", true),
                new ServiceTelemetrySpec("payment-service", goldenSignals(), requiredLogFields(), "trace_id", true),
                new ServiceTelemetrySpec("identity-service", goldenSignals(), requiredLogFields(), "trace_id", true),
                new ServiceTelemetrySpec("support-service", goldenSignals(), requiredLogFields(), "trace_id", true)
        );
    }

    private static List<SloDashboardSpec> dashboardSpecs() {
        return List.of(
                new SloDashboardSpec("ledger-service", 24, 2.0),
                new SloDashboardSpec("payment-service", 24, 2.5),
                new SloDashboardSpec("identity-service", 24, 1.8),
                new SloDashboardSpec("support-service", 24, 2.0)
        );
    }

    private static List<AlertSpec> alertSpecs() {
        return List.of(
                new AlertSpec("Tier 1 ledger imbalance", AlertTier.TIER_1, "runbooks/ledger-imbalance.md"),
                new AlertSpec("Tier 2 payment latency", AlertTier.TIER_2, "runbooks/payment-latency.md"),
                new AlertSpec("Tier 3 support backlog", AlertTier.TIER_3, "runbooks/support-backlog.md")
        );
    }

    private static DeploymentPipelineSpec pipelineSpec() {
        return new DeploymentPipelineSpec(
                List.of(
                        DeploymentStage.BUILD_TEST,
                        DeploymentStage.CONTAINER_BUILD,
                        DeploymentStage.INTEGRATION_TESTS,
                        DeploymentStage.DEPLOY_STAGING,
                        DeploymentStage.DEPLOY_PRODUCTION
                ),
                true,
                true,
                true
        );
    }

    private static DisasterRecoverySpec drSpec() {
        return new DisasterRecoverySpec(true, true, true, true, true, true, true, "RDS automated snapshots and cross-region replica");
    }

    private static List<String> goldenSignals() {
        return List.of("latency", "traffic", "errors", "saturation");
    }

    private static List<String> requiredLogFields() {
        return List.of("timestamp", "level", "service", "trace_id", "message");
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
