# VoiceSecure Wallet

## What Is This App?

VoiceSecure Wallet is a staged fintech platform for regulated payments,
wallet balances, identity, fraud, notifications, support, recovery,
operations, and launch readiness.

The program is organized as small, testable service slices because every later
capability depends on a trusted money movement core, a controlled identity
surface, and enough operational discipline to keep the system safe under load.

## Problem Statement

Financial platforms fail when their ledger, authorization, identity, support,
and recovery paths drift apart. That creates duplicate payments, impossible
reconciliation, weak audit trails, higher fraud loss, longer support calls, and
launches that reach production before the team can prove the system is ready.

VoiceSecure Wallet addresses that by making the core controls executable:
ledger invariants, payment orchestration, fraud and compliance checks, voice
authentication, recovery workflows, operational validation, and launch gates.

## Service Map

| Service | Problem Statement | Impact |
| --- | --- | --- |
| `ledger-service` | Keeps the source of truth for money movement append-only, balanced, and auditable. | Protects customer balances, keeps finance reconciliation accurate, and reduces audit risk. |
| `wallet-service` | Projects ledger events into user-facing wallet balances without writing to the ledger. | Gives users fast balance reads while keeping money movement controlled by `ledger-service`. |
| `payment-service` | Coordinates authorization, reservation, ledger commit, completion, and compensation without partial states. | Prevents duplicate charges, failed transfers, and costly manual support work. |
| `identity-service` | Issues and verifies identities, sessions, and device-bound credentials for secure access. | Lowers account takeover risk while keeping login and device recovery predictable. |
| `fraud-service` | Scores transaction risk using trust, velocity, amount, and compliance signals before payment approval. | Cuts fraud losses and reduces false declines that frustrate legitimate customers. |
| `compliance-service` | Screens identities and transactions against PEP, sanctions, and AML requirements. | Helps the business stay bankable, compliant, and ready for regulated growth. |
| `voice-service` | Provides voice enrollment, verification, liveness, replay detection, and fallback selection. | Gives users a low-friction authentication path and reduces password-reset pressure. |
| `notification-service` | Consumes payment and voice fallback events for receipts, failure notices, compensation notices, and OTP fallback. | Keeps customer communications asynchronous and prevents notification outages from blocking payments. |
| `support-service` | Gives support teams search, freeze, dispute, and repair workflows tied to ledger truth. | Shortens resolution time and gives the company traceable, defensible support actions. |
| `recovery-service` | Rebuilds trust after compromise through document upload, video KYC, reenrollment, and certificate reissue. | Lets legitimate users regain access safely instead of abandoning the account. |
| `ops-service` | Models telemetry, dashboards, alerts, release gates, and disaster recovery requirements. | Improves uptime, incident response, and leadership visibility into operational risk. |
| `launch-service` | Validates chaos, pen test, security, performance, and fallback gates before release. | Prevents unsafe releases and protects reputation, revenue, and customer trust. |

`event-core` is shared event infrastructure used by those services rather than
a product microservice.

## Current Slice

- Java 17 `ledger-service` domain model.
- Java 17 `wallet-service` CQRS balance projection.
- Java 17 `payment-service` saga core.
- Java 17 `identity-service`, `compliance-service`, and `fraud-service` cores.
- Shared event backbone with in-memory outbox relay.
- Python `voice-service` biometrics core.
- Java 17 `notification-service` event consumer for receipts and OTP fallback.
- Java 17 `support-service` and `recovery-service` cores for support workflows.
- Java 17 `ops-service` plan validator for observability and disaster recovery.
- Java 17 `launch-service` readiness validator for hardening and launch gates.
- PostgreSQL schema migration for signed, append-only ledger entries.
- In-memory repository for deterministic local tests.
- Repair API domain stub requiring a justification payload.
- Lightweight Java test runner covering reconciliation, idempotency, repairs,
  concurrent overdraft prevention, payment saga transitions, trust-layer
  checks, wallet projections, event-envelope behavior, notification dispatch
  decisions, recovery flows, support workflows, observability/DR plan
  validation, launch readiness, and voice verification flows.

## Benchmark

The current benchmark is deterministic service-level readiness. Each benchmark
is executable through the local test suite or represented as launch evidence:

| Area | Target |
| --- | --- |
| Ledger | Balanced reconciliation, no overdraft under concurrent debit attempts, idempotent retries in constant time, and conflicting idempotency keys rejected before append. |
| Wallet | Ledger events project into balances once, duplicate event IDs are ignored, and non-ledger events are rejected. |
| Payment | Happy path reaches `COMPLETED`; fraud, voice, reservation, ledger, and compensation failures reach explicit terminal states. |
| Identity | RS256 access-token verification, unknown `kid` rejection, refresh-token reuse revocation, and device-signature validation. |
| Fraud and compliance | PEP/sanctions/AML screening writes one audit trail entry and fraud policy changes happen through `FraudPolicy`. |
| Event backbone | Pending outbox messages relay in order, publish failures remain pending, and failed attempts retain last-error evidence. |
| Voice | Enrollment requires three samples, verification uses Python 3.10+, challenges are single-use, and score ranges are enforced. |
| Notifications | Payment receipts, payment failure/compensation notices, and voice OTP fallback are event-driven and idempotent by source event ID. |
| Support and recovery | Repair cases are persisted before ledger mutation; duplicate recovery transitions are rejected before external ports are called. |
| Ops | Required dashboards, alert tiers, release stages, log fields, and reconciliation cadence are policy-driven through `OpsReadinessPolicy`. |
| Launch | Chaos, security, pen test, shadow mode, 10x load, 100/100 fallback, RTO/RPO, CVE scan source, and pen-test report evidence are validated. |

## How To Use It

Prerequisites:

- Java 17 with `javac` and `java` on `PATH`.
- Python 3.10+ for `voice-service`, because it uses `dataclass(slots=True)`.
- PowerShell for the provided Windows-first test script.

Run the full suite on Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-services.ps1
```

Run the Java suite on macOS or Linux:

```sh
mkdir -p .codex_tmp/services-classes
javac -Xlint:all -d .codex_tmp/services-classes $(find services -name '*.java')
for test_file in $(find services -path '*/src/test/java/*Tests.java' | sort); do
  class_name=${test_file#*/src/test/java/}
  class_name=${class_name%.java}
  class_name=${class_name//\//.}
  java -cp .codex_tmp/services-classes "$class_name"
done
```

Run the voice tests with Python 3.10+:

```sh
python3 services/voice-service/test_voice_service.py
```

The first CI slice is defined in `.github/workflows/service-ci.yml`. It runs the
same direct Java compile/test loop, Python voice tests, and whitespace check on
pull requests and pushes to `main`.

Use each service README for the smallest code example for that service. The
remaining production plan still requires HTTP adapters, durable infrastructure,
CI/CD, contract tests, chaos tests, and launch evidence before the PDF launch
criteria can be marked complete.

## Delivery Docs

- [Release runbook](docs/release-runbook.md): launch checklist, rollout flow,
  validation gates, and rollback steps.

## Quick Test Command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-services.ps1
```
