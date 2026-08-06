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
                new TestCase("GitHub OIDC is scoped to the main branch", TerraformAwsBaselineTests::githubOidcIsScoped),
                new TestCase("GitHub OIDC can change only the staging foundation", TerraformAwsBaselineTests::githubOidcFoundationWritesAreScoped),
                new TestCase("demo is explicitly cheap and disposable", TerraformAwsBaselineTests::demoIsDisposable),
                new TestCase("staging is isolated from production", TerraformAwsBaselineTests::stagingIsIsolated),
                new TestCase("staging workflows deploy only staging", TerraformAwsBaselineTests::stagingWorkflowsAreScoped),
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

    private static void stagingIsIsolated() throws IOException {
        String staging = read("environments/staging/main.tf");
        String variables = read("environments/staging/variables.tf");
        String values = read("environments/staging/terraform.tfvars.example");
        assertContains(staging, "key = \"environments/staging.tfstate\"", "staging remote state key");
        assertContains(staging, "use_lockfile = true", "staging native state locking");
        assertContains(staging, "Environment = \"staging\"", "staging resource tag");
        assertContains(variables, "default = \"voicesecure-staging\"", "staging resource prefix");
        assertTrue(!staging.contains("production-reference"), "staging must not use the production-reference state or tags");
        assertTrue(!variables.contains("voicesecure-production"), "staging must not use production resource names");
        for (String service : List.of("rds", "redis", "msk")) {
            assertContains(values, "enable_" + service + " = false", "staging defaults " + service + " off");
        }
    }

    private static void stagingWorkflowsAreScoped() throws IOException {
        String deployment = Files.readString(Path.of("scripts", "aws-staging-deployment.sh"))
                .replaceAll("\\s+", " ");
        assertContains(deployment, "environment_dir=\"infra/aws/environments/staging\"", "staging deployment root");
        assertTrue(!deployment.contains("environment_dir=\"infra/aws/environments/production-reference\""),
                "staging workflow must never deploy the production reference");
    }

    private static void githubOidcIsScoped() throws IOException {
        String oidc = read("bootstrap/github-oidc.tf");
        assertContains(oidc, "https://token.actions.githubusercontent.com", "GitHub OIDC issuer");
        assertContains(oidc, "values = [\"sts.amazonaws.com\"]", "AWS STS audience");
        assertContains(oidc,
                "repo:${var.github_repository_owner}/${var.github_repository_name}:ref:refs/heads/${var.github_branch_name}",
                "main branch subject");
        assertContains(oidc, "sts:AssumeRoleWithWebIdentity", "web identity trust action");
        assertContains(oidc, "resource \"aws_iam_role\" \"github_actions\"", "shared GitHub Actions role");
        assertContains(oidc, "\"voice-secure-wallet\"", "shared GitHub Actions role name");
        assertContains(oidc, "aws:policy/ReadOnlyAccess", "read-only discovery policy");
        assertContains(oidc, "CreateTaggedStagingApiRepository", "tagged ECR repository creation");
        assertContains(oidc, "actions = [\"ecr:CreateRepository\"]", "ECR repository create permission");
        assertContains(oidc, "aws:RequestTag/Environment", "ECR environment request tag");
        assertContains(oidc, "aws:RequestTag/ManagedBy", "ECR managed-by request tag");
        assertContains(oidc, "aws:RequestTag/Project", "ECR project request tag");
        assertContains(
            oidc,
            "repository/${local.application_repository_name}",
            "exact staging ECR repository scope");
        assertContains(oidc, "iam:PassedToService", "task-role passing boundary");
        assertContains(oidc, "aws_kms_key.bootstrap.arn", "Terraform state KMS scope");
        assertTrue(!oidc.contains("PowerUserAccess"), "OIDC role must not have power-user access");
        assertTrue(!oidc.contains("AdministratorAccess"), "OIDC role must not have administrator access");
    }

    private static void githubOidcFoundationWritesAreScoped() throws IOException {
        String oidc = read("bootstrap/github-oidc.tf");
        assertContains(
                oidc,
                "voice-secure-wallet-staging-network",
                "dedicated staging network policy");
        assertContains(
                oidc,
                "voice-secure-wallet-staging-security",
                "dedicated staging security policy");
        assertContains(oidc, "aws:RequestTag/Environment", "staging create tag boundary");
        assertContains(oidc, "ec2:ResourceTag/Environment", "staging EC2 resource boundary");
        assertContains(oidc, "ec2:CreateAction", "tag-on-create boundary");
        assertContains(oidc, "${local.staging_name}-default", "default security group bootstrap boundary");
        assertContains(oidc, "alias/${local.staging_name}-platform", "staging KMS alias boundary");
        assertContains(oidc, "${local.staging_name}-audit-evidence", "staging audit bucket boundary");
        assertContains(oidc, "role/${local.staging_name}-vpc-flow", "staging flow-log role boundary");
        assertContains(oidc, "vpc-flow-logs.amazonaws.com", "flow-log PassRole service boundary");
        for (String forbidden : List.of("rds:", "elasticache:", "kafka:", "msk:", "AdministratorAccess", "PowerUserAccess")) {
            assertTrue(!oidc.contains(forbidden), "foundation policy must not grant " + forbidden);
        }
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
        for (String environment : List.of("demo", "staging", "production-reference")) {
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
        String staging = read("environments/staging/main.tf");
        String production = read("environments/production-reference/main.tf");
        for (String module : List.of("networking", "encryption", "database", "cache", "messaging", "audit-storage", "observability")) {
            String source = "../../modules/" + module;
            assertContains(demo, source, module + " demo composition");
            assertContains(staging, source, module + " staging composition");
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
