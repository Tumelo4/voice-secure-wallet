# contract-tests

Executable event contract and compatibility checks for VoiceSecure Wallet.

## Problem Statement

Event-driven fintech systems can pass unit tests but still fail in production
when producers publish payloads that consumers or schema registries cannot
accept. Without explicit compatibility gates, fraud, compliance, payment, and
support workflows can break during a release.

## Impact

- Consumers get predictable event payloads and compatibility guarantees.
- Release owners can block unsafe contract changes before deployment.
- The business reduces incident risk from silent event-shape drift.

## Scope

This slice validates local event payload behavior and credential-free
Pact/Schema Registry readiness. Live broker credentials, provider-state Pact
tests, and real Schema Registry calls remain environment-specific follow-up
work.

## Current Guarantees

- `fraud.scored` carries auth policy, voice threshold, approval, topic, and
  partition-key evidence.
- `compliance.hit` carries PEP hit evidence, case ID, topic, and partition-key
  evidence.
- Clear compliance results cannot publish `compliance.hit`.
- Critical event contracts require Pact broker publication, consumer
  verification, Schema Registry registration, schema ID pinning, and
  `BACKWARD_TRANSITIVE` compatibility evidence.

## Benchmark

- 3 event payload contract tests pass.
- 3 contract compatibility validator tests pass.

## How To Use It

Run the full Java service loop from the repository root:

```sh
mkdir -p .codex_tmp/services-classes
javac -Xlint:all -d .codex_tmp/services-classes $(find services -name '*.java')
java -cp .codex_tmp/services-classes com.voicesecure.contracts.EventContractTests
java -cp .codex_tmp/services-classes com.voicesecure.contracts.ContractCompatibilityValidatorTests
```
