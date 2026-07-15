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
                new TestCase("production controls are configurable", TerraformAwsBaselineTests::productionControlsAreConfigurable),
                new TestCase("private workloads use selected AWS endpoints", TerraformAwsBaselineTests::privateEndpointsAreSelectable),
                new TestCase("demo stages isolate chargeable services", TerraformAwsBaselineTests::demoStagesAreIsolated),
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
        assertContains(demo, "multi_az = var.rds_multi_az", "configurable RDS availability");
        assertContains(demo, "deletion_protection = var.rds_deletion_protection", "configurable RDS deletion protection");
        assertContains(demo, "node_count = var.redis_node_count", "configurable Redis footprint");
        assertContains(demo, "broker_count = 2", "small MSK footprint");
        assertContains(demo, "count = var.enable_msk ? 1 : 0", "optional MSK");
        assertContains(demo, "retention_days = var.log_retention_days", "configurable log retention");
        assertContains(demo, "object_lock_enabled = var.audit_object_lock_enabled", "configurable evidence lock");
    }

    private static void productionIsHardened() throws IOException {
        String production = read("environments/production-reference/main.tf");
        String values = read("environments/production-reference/terraform.tfvars.example");
        assertContains(values, "rds_multi_az = true", "Multi-AZ RDS");
        assertContains(values, "rds_deletion_protection = true", "RDS deletion protection");
        assertContains(values, "rds_performance_insights_enabled = true", "RDS Performance Insights");
        assertContains(production, "backup_retention_days = 35", "PITR retention");
        assertContains(values, "redis_node_count = 2", "HA Redis nodes");
        assertContains(values, "redis_multi_az = true", "HA Redis failover");
        assertContains(values, "enable_msk = true", "production MSK");
        assertContains(production, "broker_count = 3", "three-broker MSK");
        assertContains(values, "log_retention_days = 365", "long telemetry retention");
        assertContains(values, "audit_object_lock_enabled = true", "compliance object lock");
        assertContains(read("modules/messaging/main.tf"), "iam = true", "MSK IAM authentication");
        assertContains(read("modules/database/main.tf"), "storage_encrypted = true", "RDS encryption");
        assertContains(read("modules/cache/main.tf"), "transit_encryption_enabled = true", "Redis TLS");
    }

    private static void productionControlsAreConfigurable() throws IOException {
        for (String environment : List.of("demo", "production-reference")) {
            String variables = read("environments/" + environment + "/variables.tf");
            for (String name : List.of("enable_msk", "rds_multi_az", "rds_deletion_protection",
                    "rds_performance_insights_enabled", "redis_node_count", "redis_multi_az",
                    "audit_object_lock_enabled", "log_retention_days")) {
                assertContains(variables, "variable \"" + name + "\"", environment + " " + name + " input");
            }
        }
        assertContains(read("modules/database/main.tf"),
                "performance_insights_enabled = var.performance_insights_enabled", "RDS Performance Insights wiring");
        assertContains(read("modules/cache/main.tf"), "multi_az_enabled = var.multi_az", "Redis Multi-AZ wiring");
    }

    private static void privateEndpointsAreSelectable() throws IOException {
        String networking = read("modules/networking/main.tf");
        assertContains(networking, "variable \"interface_endpoint_services\"", "endpoint allowlist input");
        assertContains(networking, "resource \"aws_vpc_endpoint\" \"interface\"", "interface endpoints");
        assertContains(networking, "private_dns_enabled = true", "private endpoint DNS");
        assertContains(networking, "referenced_security_group_id = aws_security_group.app.id", "app-only endpoint ingress");
        assertTrue(!networking.contains("aws_nat_gateway"), "cost-controlled environments must not create a NAT Gateway");
    }

    private static void demoStagesAreIsolated() throws IOException {
        String foundation = read("environments/demo/stages/foundation.tfvars.example");
        String data = read("environments/demo/stages/data-services.tfvars.example");
        String msk = read("environments/demo/stages/msk.tfvars.example");
        for (String service : List.of("rds", "redis", "msk")) {
            assertContains(foundation, "enable_" + service + " = false", "foundation disables " + service);
        }
        assertContains(data, "enable_rds = true", "data stage enables RDS");
        assertContains(data, "enable_redis = true", "data stage enables Redis");
        assertContains(data, "enable_msk = false", "data stage defers MSK");
        assertContains(msk, "enable_msk = true", "MSK stage enables broker");
        assertTrue(Files.isRegularFile(Path.of("docs", "aws-evidence", "architecture.png")),
                "AWS evidence architecture should exist");
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
