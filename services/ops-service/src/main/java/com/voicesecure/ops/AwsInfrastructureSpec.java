package com.voicesecure.ops;

import java.util.Objects;

public record AwsInfrastructureSpec(
        String region,
        int privateSubnetCount,
        boolean kmsEnabled,
        boolean mskTlsInTransit,
        boolean mskIamAuthEnabled,
        boolean rdsMultiAz,
        boolean rdsPointInTimeRecovery,
        boolean rdsDeletionProtection,
        boolean redisMultiAz,
        boolean redisEncryptionAtRest,
        boolean redisEncryptionInTransit,
        boolean s3ObjectLockEnabled,
        boolean secretsManagerReferencesOnly
) {
    public AwsInfrastructureSpec {
        region = Objects.requireNonNull(region, "region").trim();
        if (region.isEmpty()) {
            throw new OpsException("AWS region is required");
        }
        if (privateSubnetCount <= 0) {
            throw new OpsException("private subnet count must be positive");
        }
    }
}
