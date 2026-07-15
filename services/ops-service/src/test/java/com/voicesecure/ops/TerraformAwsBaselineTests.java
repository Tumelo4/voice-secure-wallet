package com.voicesecure.ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TerraformAwsBaselineTests {
    private static final Path AWS = Path.of("infra", "aws");

    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("Terraform exposes reusable capability modules", TerraformAwsBaselineTests::modulesExist),
                new TestCase("state bootstrap is independent from workloads", TerraformAwsBaselineTests::bootstrapIsIndependent),
                new TestCase("demo is explicitly cheap and disposable", TerraformAwsBaselineTests::demoIsDisposable),
                new TestCase("production reference preserves hardened controls", TerraformAwsBaselineTests::productionIsHardened),
                new TestCase("both environments compose the same modules", TerraformAwsBaselineTests::environmentsReuseModules),
                new TestCase("secrets are inputs rather than committed values", TerraformAwsBaselineTests::secretsAreInputs)
        };
        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Terraform AWS baseline tests passed: " + tests.length);
    }

    private static void modulesExist() {
        for (String module : List.of("networking", "encryption", "database", "cache", "messaging", "audit-storage", "observability")) {
            assertTrue(Files.isRegularFile(AWS.resolve("modules").resolve(module).resolve("main.tf")), module + " module should exist");
        }
    }

    private static void bootstrapIsIndependent() throws IOException {
        String bucket = read("bootstrap/state-bucket.tf");
        String locking = read("bootstrap/state-locking.tf");
        String production = read("environments/production-reference/main.tf");
        assertContains(bucket, "resource \"aws_s3_bucket\" \"state\"", "state bucket");
        assertContains(bucket, "prevent_destroy = true", "state deletion guard");
        assertContains(locking, "resource \"aws_dynamodb_table\" \"locking\"", "locking table");
        assertContains(locking, "point_in_time_recovery", "lock PITR");
        assertContains(production, "backend \"s3\"", "production remote backend");
        assertTrue(!read("environments/demo/main.tf").contains("backend \"s3\""), "demo should use disposable local state");
    }

    private static void demoIsDisposable() throws IOException {
        String demo = read("environments/demo/main.tf");
        assertContains(demo, "instance_class = \"db.t4g.small\"", "small RDS");
        assertContains(demo, "multi_az = false", "single-AZ RDS");
        assertContains(demo, "deletion_protection = false", "disposable RDS");
        assertContains(demo, "node_count = 1", "single Redis node");
        assertContains(demo, "broker_count = 2", "small MSK footprint");
        assertContains(demo, "retention_days = 14", "short log retention");
        assertContains(demo, "object_lock_enabled = false", "disposable evidence bucket");
    }

    private static void productionIsHardened() throws IOException {
        String production = read("environments/production-reference/main.tf");
        assertContains(production, "multi_az = true", "Multi-AZ RDS");
        assertContains(production, "deletion_protection = true", "RDS deletion protection");
        assertContains(production, "backup_retention_days = 35", "PITR retention");
        assertContains(production, "node_count = 2", "HA Redis");
        assertContains(production, "broker_count = 3", "three-broker MSK");
        assertContains(production, "retention_days = 365", "long telemetry retention");
        assertContains(production, "object_lock_enabled = true", "compliance object lock");
        assertContains(read("modules/messaging/main.tf"), "iam = true", "MSK IAM authentication");
        assertContains(read("modules/database/main.tf"), "storage_encrypted = true", "RDS encryption");
        assertContains(read("modules/cache/main.tf"), "transit_encryption_enabled = true", "Redis TLS");
    }

    private static void environmentsReuseModules() throws IOException {
        String demo = read("environments/demo/main.tf");
        String production = read("environments/production-reference/main.tf");
        for (String module : List.of("networking", "encryption", "database", "cache", "messaging", "audit-storage", "observability")) {
            String source = "../../modules/" + module;
            assertContains(demo, source, module + " demo composition");
            assertContains(production, source, module + " production composition");
        }
    }

    private static void secretsAreInputs() throws IOException {
        String all = Files.walk(AWS)
                .filter(path -> path.toString().endsWith(".tf"))
                .map(path -> { try { return Files.readString(path); } catch (IOException e) { throw new IllegalStateException(e); } })
                .reduce("", (left, right) -> left + right);
        assertContains(all, "variable \"redis_auth_token\"", "Redis secret input");
        assertTrue(!all.contains("secret_string"), "secret values must not be committed");
        assertTrue(!all.contains("password = \""), "passwords must not be hard-coded");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(AWS.resolve(relative)).replaceAll("\\s+", " ");
    }
    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) throw new AssertionError(message + ": expected " + expected);
    }
    private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private record TestCase(String name, ThrowingRunnable runnable) { void run() throws Exception { runnable.run(); } }
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
