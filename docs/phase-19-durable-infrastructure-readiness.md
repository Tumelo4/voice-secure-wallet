# Phase 19 Durable Infrastructure Readiness

This slice starts the Kafka/AWS phase without requiring live cloud credentials:

- `DurableInfrastructureValidator` validates the infrastructure preflight plan;
- Kafka topics must cover the full event-topic catalog;
- topics require minimum partitions, replication factor, schema compatibility,
  dead-letter queues, and retention;
- AWS readiness requires private subnet coverage, KMS, MSK TLS/IAM, RDS
  Multi-AZ/PITR/deletion protection, Redis HA/encryption, S3 object lock, and
  managed secret references;
- custom policies can narrow topic sets or change replication requirements for
  controlled test environments.

## SOLID Notes

- **Single Responsibility:** the validator checks readiness; it does not
  provision cloud resources.
- **Open/Closed:** future Terraform, AWS SDK, or Kafka AdminClient adapters can
  produce the same plan model without changing validation rules.
- **Liskov Substitution:** default and custom policies both use the same
  validator contract.
- **Interface Segregation:** Kafka topic requirements and AWS infrastructure
  settings are modeled separately.
- **Dependency Inversion:** readiness depends on value objects and event-topic
  contracts, not live Kafka brokers or AWS credentials.

## TDD Notes

- **Red:** durable infrastructure tests first referenced missing Kafka/AWS
  readiness model and validator types.
- **Green:** topic specs, AWS specs, policy, report, and validator made valid
  plan, missing topic, weak Kafka, weak AWS, and custom policy tests pass.
- **Refactor:** README, ops README, release runbook, ubiquitous language, and
  readiness evidence now document the preflight contract.

## Live Infrastructure Boundary

This phase does not create MSK clusters, RDS databases, Redis clusters, S3
buckets, VPCs, IAM roles, mTLS ingress, or Terraform state. Those belong in the
next infrastructure-adapter/provisioning phases once live Kafka/AWS access is
available.
