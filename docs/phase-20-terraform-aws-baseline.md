# Phase 20 Terraform AWS Baseline

This slice adds a reviewable AWS Terraform baseline without applying it. The
original single-root configuration has since been refactored into reusable
capability modules with `demo` and `production-reference` compositions:

- independent provider and variable files for each environment;
- private VPC, private subnets, private route table, and S3 gateway endpoint;
- KMS key with rotation;
- MSK cluster and broker configuration for event streaming;
- RDS PostgreSQL with encryption, Multi-AZ, deletion protection, and PITR
  retention;
- ElastiCache Redis replication group with HA and encryption;
- S3 audit evidence bucket with versioning, KMS encryption, and object lock;
- Secrets Manager references for database and Redis credentials without
  committed secret values;
- outputs for downstream integration.

## SOLID Notes

- **Single Responsibility:** Terraform declares infrastructure; Java tests
  verify static safety contracts.
- **Open/Closed:** later environment modules, remote state, and live adapters can
  extend this baseline without changing the readiness validator.
- **Liskov Substitution:** staging and production can provide different tfvars
  while satisfying the same resource contract.
- **Interface Segregation:** networking, security, data services, variables, and
  outputs are split by concern.
- **Dependency Inversion:** application code depends on ports and readiness
  contracts, not direct Terraform resource details.

## TDD Notes

- **Red:** Terraform baseline tests first failed because `infra/aws` files were
  missing.
- **Green:** Terraform files declared the required VPC, KMS, MSK, RDS, Redis,
  S3 object-lock, secret-reference, and output resources.
- **Refactor:** tests normalize Terraform whitespace and documentation now
  separates static IaC validation from live AWS provisioning.

The remote-state bucket and lock table now live under `infra/aws/bootstrap` so
they are created independently before an environment consumes that backend.
The demo keeps security controls but selects disposable, lower-availability
settings; production-reference retains the hardened availability, retention,
encryption, IAM-authenticated messaging, and deletion controls.

## Live Provisioning Boundary

This phase does not run `terraform init`, create remote state, configure IAM
roles, apply against AWS, or run live MSK/RDS/Redis/S3 integration tests. Those
belong in the next provisioning and adapter phases.
