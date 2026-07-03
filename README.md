# VoiceSecure Wallet

## What Is This App?

VoiceSecure Wallet is a staged fintech platform for regulated payments,
wallet balances, identity, fraud, notifications, support, recovery,
operations, and launch readiness.

The program is organized as small, testable service slices because every later
capability depends on a trusted money movement core, a controlled identity
surface, and enough operational discipline to keep the system safe under load.

The implementation uses three complementary engineering styles:

- **TDD:** every new behavior starts with a failing test, then the smallest
  green implementation, then refactor.
- **BDD:** cross-service behaviors are captured as scenario tests in product
  language.
- **DDD:** each service owns a bounded context and communicates through ports,
  events, or explicit adapters.

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
| `api-adapter-service` | Translates HTTP-style payment commands and wallet balance reads into domain service calls, with runtime guards for auth, traceability, rate limiting, and request logging. | Gives clients stable JSON contracts while keeping domain services framework-independent. |

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
- Java 17 durable infrastructure readiness validator for Kafka topics and AWS
  HA/encryption controls.
- Java 17 `launch-service` readiness validator for hardening and launch gates.
- Java 17 `api-adapter-service` contracts and runtime boundary for payment
  commands, wallet balance reads, auth, traceability, rate limiting, and
  request logging, plus a local JDK HTTP listener.
- React Native TypeScript `apps/mobile` readiness dashboard using
  NativeWind/Tailwind CSS and Redux Toolkit, with typed API client and fetch
  transport boundaries, mobile token-session ports, Redux API flows, and
  local resilience policy.
- PostgreSQL schema migration for signed, append-only ledger entries.
- In-memory repository for deterministic local tests.
- Repair API domain stub requiring a justification payload.
- Lightweight Java test runner covering reconciliation, idempotency, repairs,
  concurrent overdraft prevention, payment saga transitions, trust-layer
  checks, wallet projections, event-envelope behavior, notification dispatch
  decisions, fraud/compliance event contracts, BDD acceptance scenarios,
  recovery flows, support workflows, API adapter/runtime guards,
  observability/DR plan validation, launch readiness, voice verification flows,
  and mobile dashboard checks.

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
| Event contracts | `fraud.scored` carries auth policy and voice threshold; `compliance.hit` carries hit evidence and cannot be emitted for clear screening results. |
| Event backbone | Pending outbox messages relay in order, publish failures remain pending, and failed attempts retain last-error evidence. |
| Voice | Enrollment requires three samples, verification uses Python 3.10+, challenges are single-use, and score ranges are enforced. |
| Notifications | Payment receipts, payment failure/compensation notices, and voice OTP fallback are event-driven and idempotent by source event ID. |
| Acceptance | BDD scenarios prove that voice fallback can complete payment, compliance hits block funds movement, and wallet projections follow ledger truth. |
| Support and recovery | Repair cases are persisted before ledger mutation; duplicate recovery transitions are rejected before external ports are called. |
| Ops | Required dashboards, alert tiers, release stages, log fields, and reconciliation cadence are policy-driven through `OpsReadinessPolicy`. |
| Durable infrastructure | Kafka topics must cover the event catalog with minimum partitions, replication, schema compatibility, DLQs, and retention; AWS readiness requires private subnets, KMS, MSK TLS/IAM, RDS HA/PITR/deletion protection, Redis encryption, S3 object lock, and managed secrets. |
| Launch | Chaos, security, pen test, shadow mode, 10x load, 100/100 fallback, RTO/RPO, CVE scan source, and pen-test report evidence are validated. |
| API adapters | Payment POST validates idempotency and trace headers, maps conflicts to `409`, wallet balance reads return JSON, and unknown routes return JSON `404`. |
| API runtime | Protected routes require bearer tokens, invalid tokens return `403`, trace IDs are required before routing, rate limits return `429`, and request outcomes are logged. |
| API local HTTP listener | Local socket tests prove wallet GET, payment POST JSON, runtime auth/trace guards, JSON headers, request logging, and rate-limit `Retry-After` propagation through the JDK HTTP server boundary. |
| Mobile UI | React Native TypeScript stack declaration, Redux readiness state, mobile accessibility labels, dashboard section order, and NativeWind/Tailwind class tokens are covered by Node tests. |
| Mobile API client | Payment commands and wallet balance reads use a typed transport port, runtime headers, API error mapping, and Redux-friendly async request states. |
| Mobile fetch transport | React Native fetch calls are isolated behind `ApiTransport`, base URLs and paths join safely, response headers/body are preserved, network failures map to typed `503` errors, and a token-provider port supports rotating credentials. |
| Mobile token session | Secure token sessions are stored behind a vault port, corrupt sessions are cleared, cached access tokens are reused before the refresh window, expiring tokens are refreshed, and failed refresh clears stored credentials. |
| Mobile Redux API flows | Wallet-balance and payment-start thunks dispatch loading, success, and failure branches into Redux request state while preserving trace IDs, previous data, API errors, and auth-session failures. |
| Mobile resilience policy | Retryable transport failures use capped exponential backoff, auth/validation failures do not retry, offline payment commands enqueue idempotently, local queue depth is capped, and queued payments drain in order. |

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

Run the voice tests with a Python 3.10+ executable:

```sh
python services/voice-service/test_voice_service.py
```

Run the mobile UI tests:

```sh
node --disable-warning=MODULE_TYPELESS_PACKAGE_JSON --test apps/mobile/test/*.test.ts
```

The first CI slice is defined in `.github/workflows/service-ci.yml`. It runs the
same direct Java compile/test loop, Python voice tests, mobile UI tests, and
whitespace check on pull requests and pushes to `main`.

Use each service README for the smallest code example for that service. The
remaining production plan still requires live Kafka/AWS provisioning,
production ingress, mTLS, native mobile keystore wiring, screen-level mobile
command forms, full Pact/Schema Registry checks, chaos tests, and launch
evidence before the PDF launch criteria can be marked complete.

## Delivery Docs

- [Ubiquitous language](docs/ubiquitous-language.md): DDD bounded contexts,
  domain terms, and TDD/BDD/DDD testing language.
- [Release runbook](docs/release-runbook.md): launch checklist, rollout flow,
  validation gates, and rollback steps.

## Quick Test Command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-services.ps1
```
