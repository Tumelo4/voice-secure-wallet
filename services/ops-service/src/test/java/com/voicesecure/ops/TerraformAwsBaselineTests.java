package com.voicesecure.ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TerraformAwsBaselineTests {
    private static final Path INFRA_DIR = Path.of("infra", "aws");

    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("Terraform baseline exposes expected files", TerraformAwsBaselineTests::terraformFilesExist),
                new TestCase("Terraform baseline declares remote state controls", TerraformAwsBaselineTests::remoteStateControlsExist),
                new TestCase("Terraform baseline declares least-privilege IAM controls", TerraformAwsBaselineTests::leastPrivilegeIamControlsExist),
                new TestCase("Terraform baseline declares strict ingress security groups", TerraformAwsBaselineTests::strictIngressSecurityGroupsExist),
                new TestCase("Terraform baseline hardens the audit evidence bucket", TerraformAwsBaselineTests::auditEvidenceBucketControlsExist),
                new TestCase("Terraform baseline declares private network and KMS controls", TerraformAwsBaselineTests::networkAndKmsControlsExist),
                new TestCase("Terraform baseline declares MSK durability controls", TerraformAwsBaselineTests::mskDurabilityControlsExist),
                new TestCase("Terraform baseline declares RDS Redis S3 durability controls", TerraformAwsBaselineTests::dataStoreDurabilityControlsExist),
                new TestCase("Terraform baseline avoids committed secret values", TerraformAwsBaselineTests::secretsAreReferencesOnly)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Terraform AWS baseline tests passed: " + tests.length);
    }

    private static void terraformFilesExist() throws IOException {
        for (String file : List.of("README.md", "versions.tf", "backend.tf", "state.tf", "iam.tf", "variables.tf", "networking.tf", "security.tf", "security-groups.tf", "data.tf", "audit.tf", "outputs.tf", "terraform.tfvars.example")) {
            assertTrue(Files.isRegularFile(INFRA_DIR.resolve(file)), file + " should exist");
        }
    }

    private static void remoteStateControlsExist() throws IOException {
        String backend = read("backend.tf");
        String state = read("state.tf");

        assertContains(backend, "backend \"s3\"", "S3 backend");
        assertContains(backend, "bucket = \"voicesecure-terraform-state\"", "remote state bucket");
        assertContains(backend, "key = \"environments/voicesecure.tfstate\"", "state key");
        assertContains(backend, "dynamodb_table = \"voicesecure-terraform-locks\"", "state lock table");
        assertContains(backend, "encrypt = true", "state encryption");
        assertContains(backend, "workspace_key_prefix = \"voicesecure\"", "workspace key prefix");

        assertContains(state, "resource \"aws_s3_bucket\" \"terraform_state\"", "terraform state bucket");
        assertContains(state, "resource \"aws_s3_bucket_versioning\" \"terraform_state\"", "terraform state versioning");
        assertContains(state, "resource \"aws_s3_bucket_server_side_encryption_configuration\" \"terraform_state\"", "terraform state encryption");
        assertContains(state, "resource \"aws_s3_bucket_public_access_block\" \"terraform_state\"", "terraform state public access block");
        assertContains(state, "resource \"aws_dynamodb_table\" \"terraform_locks\"", "terraform state lock table");
        assertContains(state, "billing_mode = \"PAY_PER_REQUEST\"", "lock table billing mode");
        assertContains(state, "hash_key = \"LockID\"", "lock table hash key");
    }

    private static void leastPrivilegeIamControlsExist() throws IOException {
        String iam = read("iam.tf");

        assertContains(iam, "resource \"aws_iam_role\" \"api\"", "api role");
        assertContains(iam, "resource \"aws_iam_role\" \"payment\"", "payment role");
        assertContains(iam, "resource \"aws_iam_role\" \"ledger\"", "ledger role");
        assertContains(iam, "resource \"aws_iam_role\" \"wallet\"", "wallet role");
        assertContains(iam, "resource \"aws_iam_role\" \"compliance\"", "compliance role");
        assertContains(iam, "resource \"aws_iam_role\" \"support\"", "support role");
        assertContains(iam, "resource \"aws_iam_role\" \"ci_deploy\"", "ci deploy role");
        assertContains(iam, "resource \"aws_iam_role\" \"break_glass_admin\"", "break-glass role");
        assertContains(iam, "secretsmanager:GetSecretValue", "secret access should be scoped");
        assertContains(iam, "dynamodb:PutItem", "ci deploy should lock state");
        assertContains(iam, "s3:PutObject", "ci deploy should write state");
        assertTrue(!iam.contains("AdministratorAccess"), "Terraform must not attach broad admin policies");
    }

    private static void strictIngressSecurityGroupsExist() throws IOException {
        String securityGroups = read("security-groups.tf");

        assertContains(securityGroups, "resource \"aws_security_group\" \"alb\"", "ALB security group");
        assertContains(securityGroups, "resource \"aws_security_group\" \"app\"", "app security group");
        assertContains(securityGroups, "resource \"aws_security_group\" \"msk\"", "MSK security group");
        assertContains(securityGroups, "resource \"aws_security_group\" \"database\"", "database security group");
        assertContains(securityGroups, "resource \"aws_security_group\" \"redis\"", "redis security group");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_ingress_rule\" \"alb_https\"", "ALB HTTPS ingress rule");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_egress_rule\" \"alb_to_app\"", "ALB to app egress rule");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_ingress_rule\" \"app_from_alb\"", "app ingress rule");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_egress_rule\" \"app_to_database\"", "app to database egress rule");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_egress_rule\" \"app_to_redis\"", "app to redis egress rule");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_egress_rule\" \"app_to_msk\"", "app to MSK egress rule");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_ingress_rule\" \"msk_from_app\"", "MSK ingress rule");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_ingress_rule\" \"database_from_app\"", "database ingress rule");
        assertContains(securityGroups, "resource \"aws_vpc_security_group_ingress_rule\" \"redis_from_app\"", "redis ingress rule");
        assertContains(securityGroups, "from_port = 443", "public HTTPS ingress");
        assertContains(securityGroups, "cidr_ipv4 = \"0.0.0.0/0\"", "public HTTPS from internet");
        assertContains(securityGroups, "from_port = var.app_port", "app port wiring");
        assertContains(securityGroups, "from_port = 5432", "PostgreSQL port");
        assertContains(securityGroups, "from_port = 6379", "Redis port");
        assertContains(securityGroups, "from_port = 9098", "MSK port");
        assertContains(securityGroups, "egress = []", "private data-plane egress should be removed");
        assertContains(securityGroups, "ingress = []", "security groups should not use default inline ingress");
        assertEquals(1, countOccurrences(securityGroups, "0.0.0.0/0"), "only the ALB may expose a public CIDR");
    }

    private static void auditEvidenceBucketControlsExist() throws IOException {
        String audit = read("audit.tf");

        assertContains(audit, "resource \"aws_s3_bucket\" \"audit_evidence\"", "audit bucket");
        assertContains(audit, "object_lock_enabled = true", "object lock enabled");
        assertContains(audit, "resource \"aws_s3_bucket_server_side_encryption_configuration\" \"audit_evidence\"", "audit bucket encryption");
        assertContains(audit, "resource \"aws_s3_bucket_versioning\" \"audit_evidence\"", "audit bucket versioning");
        assertContains(audit, "resource \"aws_s3_bucket_public_access_block\" \"audit_evidence\"", "audit bucket public access block");
        assertContains(audit, "resource \"aws_s3_bucket_policy\" \"audit_evidence\"", "audit bucket policy");
        assertContains(audit, "aws:SecureTransport", "TLS-only audit policy");
        assertContains(audit, "resource \"aws_s3_bucket_object_lock_configuration\" \"audit_evidence\"", "audit bucket object lock configuration");
    }

    private static void networkAndKmsControlsExist() throws IOException {
        String networking = read("networking.tf");
        String security = read("security.tf");

        assertContains(networking, "resource \"aws_vpc\" \"voice_secure\"", "VPC resource");
        assertContains(networking, "resource \"aws_subnet\" \"private\"", "private subnet resource");
        assertContains(networking, "resource \"aws_vpc_endpoint\" \"s3\"", "S3 VPC endpoint");
        assertContains(security, "resource \"aws_kms_key\" \"platform\"", "platform KMS key");
        assertContains(security, "enable_key_rotation = true", "KMS rotation");
    }

    private static void mskDurabilityControlsExist() throws IOException {
        String data = read("data.tf");

        assertContains(data, "resource \"aws_msk_cluster\" \"events\"", "MSK cluster");
        assertContains(data, "number_of_broker_nodes = var.msk_broker_count", "MSK broker count");
        assertContains(data, "client_broker = \"TLS\"", "MSK TLS in transit");
        assertContains(data, "iam = true", "MSK IAM auth");
        assertContains(data, "resource \"aws_msk_configuration\" \"events\"", "MSK configuration");
        assertContains(data, "min.insync.replicas=2", "MSK insync replicas");
    }

    private static void dataStoreDurabilityControlsExist() throws IOException {
        String data = read("data.tf");

        assertContains(data, "resource \"aws_db_instance\" \"ledger\"", "RDS instance");
        assertContains(data, "multi_az = true", "RDS Multi-AZ");
        assertContains(data, "deletion_protection = true", "RDS deletion protection");
        assertContains(data, "backup_retention_period = var.rds_backup_retention_days", "RDS PITR retention");
        assertContains(data, "resource \"aws_elasticache_replication_group\" \"api_rate_limits\"", "Redis replication group");
        assertContains(data, "at_rest_encryption_enabled = true", "Redis at-rest encryption");
        assertContains(data, "transit_encryption_enabled = true", "Redis transit encryption");
    }

    private static void secretsAreReferencesOnly() throws IOException {
        String all = readAllTerraform();

        assertContains(all, "resource \"aws_secretsmanager_secret\" \"database_password\"", "database password secret reference");
        assertContains(all, "resource \"aws_secretsmanager_secret\" \"redis_auth_token\"", "redis token secret reference");
        assertTrue(!all.contains("secret_string"), "Terraform must not commit secret values");
        assertTrue(!all.contains("password = \""), "Terraform must not hard-code passwords");
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(INFRA_DIR.resolve(fileName)).replaceAll("\\s+", " ");
    }

    private static String readAllTerraform() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String file : List.of("versions.tf", "backend.tf", "state.tf", "variables.tf", "networking.tf", "security.tf", "security-groups.tf", "data.tf", "audit.tf", "outputs.tf")) {
            builder.append(read(file)).append('\n');
        }
        return builder.toString();
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + ": expected to find " + expected);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static int countOccurrences(String actual, String expected) {
        int count = 0;
        int index = 0;
        while ((index = actual.indexOf(expected, index)) >= 0) {
            count++;
            index += expected.length();
        }
        return count;
    }

    private record TestCase(String name, ThrowingRunnable runnable) {
        void run() throws Exception {
            runnable.run();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
