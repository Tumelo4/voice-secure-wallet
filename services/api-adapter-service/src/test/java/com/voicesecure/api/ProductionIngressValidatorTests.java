package com.voicesecure.api;

import java.util.Set;

public final class ProductionIngressValidatorTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("valid production ingress plan passes", ProductionIngressValidatorTests::validPlanPasses),
                new TestCase("transport security gaps block ingress", ProductionIngressValidatorTests::transportSecurityGapsBlockIngress),
                new TestCase("runtime control gaps block ingress", ProductionIngressValidatorTests::runtimeControlGapsBlockIngress)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Production ingress validator tests passed: " + tests.length);
    }

    private static void validPlanPasses() {
        ProductionIngressValidationReport report = new ProductionIngressValidator().validate(validPlan());

        assertTrue(report.ready(), "valid production ingress plan should pass");
        assertEquals(0, report.blockers().size(), "valid plan blockers");
    }

    private static void transportSecurityGapsBlockIngress() {
        ProductionIngressPlan plan = new ProductionIngressPlan(
                false,
                TlsVersion.TLS_1_2,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                128,
                Set.of("/health/live", "/health/ready")
        );

        ProductionIngressValidationReport report = new ProductionIngressValidator().validate(plan);

        assertTrue(!report.ready(), "transport security gaps should block ingress");
        assertTrue(report.blockers().contains("production ingress must terminate TLS"), "TLS termination blocker");
        assertTrue(report.blockers().contains("production ingress must require TLS 1.3 or newer"), "TLS version blocker");
        assertTrue(report.blockers().contains("production ingress must require mutual TLS"), "mTLS blocker");
        assertTrue(report.blockers().contains("mTLS client certificate identity must be forwarded to the runtime"), "client certificate blocker");
    }

    private static void runtimeControlGapsBlockIngress() {
        ProductionIngressPlan plan = new ProductionIngressPlan(
                true,
                TlsVersion.TLS_1_3,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                1024,
                Set.of("/health/live", "/admin/users")
        );

        ProductionIngressValidationReport report = new ProductionIngressValidator().validate(plan);

        assertTrue(!report.ready(), "runtime control gaps should block ingress");
        assertTrue(report.blockers().contains("OIDC/JWKS provider must be configured"), "external auth blocker");
        assertTrue(report.blockers().contains("rate limits must use a distributed store"), "distributed rate-limit blocker");
        assertTrue(report.blockers().contains("WAF must be enabled before public ingress"), "WAF blocker");
        assertTrue(report.blockers().contains("HSTS must be enabled at the edge"), "HSTS blocker");
        assertTrue(report.blockers().contains("trace headers must be forwarded from ingress to runtime"), "trace forwarding blocker");
        assertTrue(report.blockers().contains("request body limit must be at most 256 KB"), "body limit blocker");
        assertTrue(report.blockers().contains("public health paths must expose /health/live and /health/ready only"), "health path blocker");
        assertTrue(report.blockers().contains("admin routes must not be publicly exposed"), "admin exposure blocker");
    }

    private static ProductionIngressPlan validPlan() {
        return new ProductionIngressPlan(
                true,
                TlsVersion.TLS_1_3,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                128,
                Set.of("/health/live", "/health/ready")
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
