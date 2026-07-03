# VoiceSecure AWS Baseline

Terraform baseline for the first production infrastructure shape.

## Scope

This module declares the durable AWS resources required by the platform:

- private VPC subnets and an S3 VPC endpoint;
- KMS key with rotation;
- MSK cluster placeholder for the event backbone;
- RDS PostgreSQL instance for ledger and wallet data;
- ElastiCache Redis replication group for distributed rate limits;
- S3 bucket with object lock for audit evidence;
- Secrets Manager secret references without committed secret values.

## Boundary

This is an infrastructure-as-code baseline, not a live deployment. It should be
reviewed, planned, and connected to remote state, IAM, mTLS ingress, and
environment-specific values before applying against AWS.

## Local Validation

The Java service test suite statically verifies the required Terraform resource
contracts and checks that secret values are not committed.
