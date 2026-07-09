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
| `ledger-service` | Keeps the source of truth for money movement append-only, balanced, and auditable, with a Postgres-backed production repository. | Protects customer balances, keeps finance reconciliation accurate, and reduces audit risk. |
| `wallet-service` | Projects ledger events into user-facing wallet balances without writing to the ledger. | Gives users fast balance reads while keeping money movement controlled by `ledger-service`. |
| `payment-service` | Coordinates authorization, reservation, ledger commit, completion, and compensation without partial states, with a Postgres-backed production repository. | Prevents duplicate charges, failed transfers, and costly manual support work. |
| `identity-service` | Issues and verifies identities, sessions, and device-bound credentials for secure access. | Lowers account takeover risk while keeping login and device recovery predictable. |
| `fraud-service` | Scores transaction risk using trust, velocity, amount, and compliance signals before payment approval. | Cuts fraud losses and reduces false declines that frustrate legitimate customers. |
| `compliance-service` | Screens identities and transactions against PEP, sanctions, and AML requirements. | Helps the business stay bankable, compliant, and ready for regulated growth. |
| `voice-service` | Provides voice enrollment, verification, liveness, replay detection, and fallback selection. | Gives users a low-friction authentication path and reduces password-reset pressure. |
| `notification-service` | Consumes payment and voice fallback events for receipts, failure notices, compensation notices, and OTP fallback. | Keeps customer communications asynchronous and prevents notification outages from blocking payments. |
| `support-service` | Gives support teams search, freeze, dispute, and repair workflows tied to ledger truth. | Shortens resolution time and gives the company traceable, defensible support actions. |
| `recovery-service` | Rebuilds trust after compromise through document upload, video KYC, reenrollment, and certificate reissue. | Lets legitimate users regain access safely instead of abandoning the account. |
| `ops-service` | Models telemetry, dashboards, alerts, release gates, and disaster recovery requirements. | Improves uptime, incident response, and leadership visibility into operational risk. |
| `launch-service` | Validates chaos, pen test, security, performance, and fallback gates before release. | Prevents unsafe releases and protects reputation, revenue, and customer trust. |
| `api-adapter-service` | Translates HTTP-style payment commands and wallet balance reads into domain service calls, with runtime guards for auth, scope-based authorization, traceability, rate limiting, and request logging. | Gives clients stable JSON contracts while keeping domain services framework-independent. |

`event-core` is shared event infrastructure used by those services rather than
a product microservice. It now includes a Kafka publication boundary that can
be wired to AWS MSK without changing domain event code.

## Current Slice

- Java 17 `ledger-service` domain model with a Postgres-backed production repository.
- Java 17 `wallet-service` CQRS balance projection.
- Java 17 `payment-service` saga core with a Postgres-backed production repository.
- Java 17 `identity-service`, `compliance-service`, and `fraud-service` cores.
- Shared event backbone with in-memory outbox relay and Kafka publication boundary.
- Python `voice-service` biometrics core.
- Java 17 `notification-service` event consumer for receipts and OTP fallback.
- Java 17 `support-service` and `recovery-service` cores for support workflows.
- Java 17 `ops-service` plan validator for observability and disaster recovery.
- Java 17 durable infrastructure readiness validator for Kafka topics and AWS
  HA/encryption controls.
- Terraform AWS baseline for VPC, KMS, MSK, RDS, Redis, S3 audit evidence
  hardening, strict security groups, and Secrets Manager references, plus
  remote state bootstrap, IAM roles, secret rotation, and locking.
- Java 17 `launch-service` readiness validator for hardening, production
  cutover, and launch gates.
- Java 17 `api-adapter-service` contracts and runtime boundary for payment
  commands, wallet balance reads, support repair requests, auth,
  route-scoped authorization, traceability, rate limiting, and request logging,
  plus a local JDK HTTP listener, public health routes, and production ingress
  readiness validator.
- React Native TypeScript `apps/mobile` readiness dashboard using
  NativeWind/Tailwind CSS and Redux Toolkit, with typed API client and fetch
  transport boundaries, mobile token-session and native secure-store ports,
  Redux API flows, screen command forms, and local resilience policy.
- PostgreSQL schema migrations for signed, append-only ledger entries and durable payment saga snapshots.
- PostgreSQL migration smoke test script that validates the ledger and payment
  migrations with `psql` against a scratch database.
- Kafka record publishing adapter for MSK-compatible event delivery.
- In-memory repository for deterministic local tests.
- Support repair API route requiring a mandatory justification payload.
- Lightweight Java test runners covering service slices, BDD acceptance
  scenarios, and contract compatibility checks.

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
| Contract compatibility | Pact broker reachability, Pact publication, consumer verification, Schema Registry reachability, schema registration, schema ID pinning, and `BACKWARD_TRANSITIVE` compatibility are validated for critical event contracts. |
| Event backbone | Pending outbox messages relay in order, publish failures remain pending, and failed attempts retain last-error evidence. |
| Voice | Enrollment requires three samples, verification uses Python 3.9+ with a slots fallback, challenges are single-use, and score ranges are enforced. |
| Notifications | Payment receipts, payment failure/compensation notices, and voice OTP fallback are event-driven and idempotent by source event ID. |
| Acceptance | BDD scenarios prove that voice fallback can complete payment, compliance hits block funds movement, and wallet projections follow ledger truth. |
| Support and recovery | Repair cases are persisted before ledger mutation; duplicate recovery transitions are rejected before external ports are called. |
| Ops | Required dashboards, alert tiers, release stages, log fields, and reconciliation cadence are policy-driven through `OpsReadinessPolicy`. |
| Durable infrastructure | Kafka topics must cover the event catalog with minimum partitions, replication, schema compatibility, DLQs, and retention; AWS readiness requires private subnets, KMS, MSK TLS/IAM, RDS HA/PITR/deletion protection, Redis encryption, S3 object lock, and managed secrets. |
| Terraform AWS baseline | Static tests verify required Terraform files, private networking, KMS rotation, MSK TLS/IAM and broker config, RDS HA/PITR, Redis encryption, S3 object lock, and no committed secret values. |
| Launch | Chaos, security, pen test, shadow mode, 10x load, 100/100 fallback, RTO/RPO, CVE scan source, pen-test report evidence, production change ticket, rollback drill, feature-flag lock, monitoring, on-call, support briefing, and 30-minute rollback readiness are validated. |
| API adapters | Payment POST validates idempotency and trace headers, support repair POST requires a mandatory justification payload, wallet balance reads return JSON, and unknown routes return JSON `404`. |
| API runtime | Protected routes require bearer tokens and route scopes, invalid tokens return `403`, trace IDs are required before routing, rate limits return `429`, and request outcomes are logged. |
| API local HTTP listener | Local socket tests prove wallet GET, payment POST JSON, public health GET, runtime auth/trace guards, JSON headers, request logging, and rate-limit `Retry-After` propagation through the JDK HTTP server boundary. |
| API production ingress | TLS 1.3, mTLS, forwarded client certificate identity, OIDC/JWKS, distributed rate limits, WAF, HSTS, trace forwarding, 256 KB request body limits, health-only public paths, and blocked admin exposure are validated before production. |
| Mobile UI | React Native TypeScript stack declaration, Redux readiness state, mobile accessibility labels, dashboard section order, and NativeWind/Tailwind class tokens are covered by Node tests. |
| Mobile API client | Payment commands and wallet balance reads use a typed transport port, runtime headers, API error mapping, and Redux-friendly async request states. |
| Mobile command forms | Wallet-balance and payment-start screen forms trim user input, build typed commands, dispatch existing Redux API flows, and block invalid local input before the API client is called. |
| Mobile fetch transport | React Native fetch calls are isolated behind `ApiTransport`, base URLs and paths join safely, response headers/body are preserved, network failures map to typed `503` errors, and a token-provider port supports rotating credentials. |
| Mobile token session | Secure token sessions are stored behind a vault port, native secure-store adapters require encrypted hardware-backed device-only storage, corrupt sessions are cleared, cached access tokens are reused before the refresh window, expiring tokens are refreshed, and failed refresh clears stored credentials. |
| Mobile Redux API flows | Wallet-balance and payment-start thunks dispatch loading, success, and failure branches into Redux request state while preserving trace IDs, previous data, API errors, and auth-session failures. |
| Mobile resilience policy | Retryable transport failures use capped exponential backoff, auth/validation failures do not retry, offline payment commands enqueue idempotently, local queue depth is capped, and queued payments drain in order. |

## How To Use It

Prerequisites:

- Java 17 with `javac` and `java` on `PATH`.
- Python 3.9+ for `voice-service`, because it uses standard dataclasses with a slots fallback on newer interpreters.
- PowerShell for the provided Windows-first test script.

Run the full suite on Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-suite.ps1
```

Run the Java service suite on macOS or Linux:

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

Run the cross-service verification suite on Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-verification.ps1
```

Run the cross-service verification suite on macOS or Linux:

```sh
mkdir -p .codex_tmp/verification-classes
javac -Xlint:all -d .codex_tmp/verification-classes $(find services tests -name '*.java')
for test_file in $(find tests -path '*/src/test/java/*Tests.java' | sort); do
  class_name=${test_file#*/src/test/java/}
  class_name=${class_name%.java}
  class_name=${class_name//\//.}
  java -cp .codex_tmp/verification-classes "$class_name"
done
```

Run the voice tests with a Python 3.9+ executable:

```sh
python3 services/voice-service/test_voice_service.py
```

Run the mobile UI tests:

```sh
cd apps/mobile
npm install
npm test
```

The first CI slice is defined in `.github/workflows/service-ci.yml`. It runs the
same service and verification runners, mobile UI tests, and whitespace check on
pull requests and pushes to `main`.

Use each service README for the smallest code example for that service. The
remaining production plan still requires applying Terraform in a real AWS
account, live Kafka/AWS integration tests, deployed ingress certificates and
DNS, real iOS Keychain/Android Keystore package QA, screen-level mobile command
dependency injection and device QA, live Pact broker and Schema Registry
credentials with provider-state verification, chaos tests, 48-hour staging
evidence, and real production cutover sign-offs before the PDF launch criteria
can be marked complete.

## Delivery Docs

- [Ubiquitous language](docs/ubiquitous-language.md): DDD bounded contexts,
  domain terms, and TDD/BDD/DDD testing language.
- [Release runbook](docs/release-runbook.md): launch checklist, rollout flow,
  validation gates, and rollback steps.

## Quick Test Command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-suite.ps1
```
