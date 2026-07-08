# Phase 25 Pact Schema Registry Readiness

This phase adds a credential-free compatibility gate before live Pact broker and
Schema Registry integration. It keeps existing event payload tests focused on
domain event shape, while a new validator models release evidence for contract
publication, consumer verification, schema registration, schema ID pinning, and
schema compatibility.

## What Changed

- Added `ContractCompatibilityPlan` and `ContractArtifact`.
- Added `ContractCompatibilityPolicy` and `SchemaCompatibilityMode`.
- Added `ContractCompatibilityValidator` and
  `ContractCompatibilityValidationReport`.
- Added cross-service verification tests for valid compatibility evidence,
  missing Pact publication/consumer verification, and Schema Registry gaps.
- Added `tests/contract-tests/README.md`.
- Updated README, release runbook, ubiquitous language, and mobile readiness
  evidence.

## TDD Trail

- **Red:** contract compatibility tests referenced missing plan, artifact,
  schema compatibility, validator, and report types.
- **Green:** the value objects and validator made Pact and Schema Registry
  readiness checks pass.
- **Refactor:** docs now distinguish local compatibility readiness from live
  Pact broker and Schema Registry credentials.

## BDD/DDD Notes

- **BDD:** test names describe release behavior: Pact publication, consumer
  verification, and Schema Registry compatibility are required before release.
- **DDD:** compatibility readiness sits beside contract tests because it
  validates event contract evidence, not fraud/compliance business decisions.

## SOLID Notes

- **Single Responsibility:** event payload tests prove event shape; the
  compatibility validator proves release evidence.
- **Open/Closed:** new event types can be added through policy/artifacts without
  changing existing event producers.
- **Liskov Substitution:** local evidence and future live broker evidence can
  satisfy the same plan shape.
- **Interface Segregation:** the validator depends only on compatibility
  evidence, not on broker clients.
- **Dependency Inversion:** compatibility readiness depends on value objects
  instead of Pact or Schema Registry SDKs.

## Still Not Production Complete

Real production readiness still requires live Pact broker credentials, provider
state verification, Schema Registry credentials, subject naming conventions, and
CI jobs that call those external services for release candidates.
