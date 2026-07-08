# VoiceSecure AWS Baseline

Terraform baseline for the first production infrastructure shape.

## Scope

This module declares the durable AWS resources required by the platform:

- private VPC subnets and an S3 VPC endpoint;
- an S3 remote-state backend with DynamoDB locking for environment-specific keys;
- KMS key with rotation;
- least-privilege IAM service roles for API, payment, ledger, wallet,
  compliance, support, CI deploy, and break-glass access;
- strict ALB, app, database, Redis, and MSK security groups with least-access
  ingress rules;
- Secrets Manager rotation hooks for database and Redis credentials;
- MSK cluster placeholder for the event backbone;
- RDS PostgreSQL instance for ledger and wallet data;
- ElastiCache Redis replication group for distributed rate limits;
- S3 audit evidence bucket with object lock, versioning, public access block,
  and TLS-only policy;
- Secrets Manager secret references without committed secret values.

## Boundary

This is an infrastructure-as-code baseline, not a live deployment. It should be
reviewed, planned, and connected to remote state, IAM, strict ingress, mTLS,
secret rotation, and environment-specific values before applying against AWS.

## Local Validation

The Java service test suite statically verifies the required Terraform resource
contracts and checks that secret values are not committed.
