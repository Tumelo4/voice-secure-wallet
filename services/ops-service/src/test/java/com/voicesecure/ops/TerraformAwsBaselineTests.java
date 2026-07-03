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
        for (String file : List.of("README.md", "versions.tf", "variables.tf", "networking.tf", "security.tf", "data.tf", "outputs.tf", "terraform.tfvars.example")) {
            assertTrue(Files.isRegularFile(INFRA_DIR.resolve(file)), file + " should exist");
        }
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
        assertContains(data, "resource \"aws_s3_bucket_object_lock_configuration\" \"audit_evidence\"", "S3 object lock");
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
        for (String file : List.of("versions.tf", "variables.tf", "networking.tf", "security.tf", "data.tf", "outputs.tf")) {
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
