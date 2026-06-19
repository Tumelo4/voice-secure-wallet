# VoiceSecure Wallet

VoiceSecure Wallet is a staged fintech platform for regulated payments,
identity, fraud, support, recovery, operations, and launch readiness.

The program is organized as small, testable service slices because every later
capability depends on a trusted money movement core, a controlled identity
surface, and enough operational discipline to keep the system safe under load.

## Platform Problem Statement

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
| `payment-service` | Coordinates authorization, reservation, ledger commit, completion, and compensation without partial states. | Prevents duplicate charges, failed transfers, and costly manual support work. |
| `identity-service` | Issues and verifies identities, sessions, and device-bound credentials for secure access. | Lowers account takeover risk while keeping login and device recovery predictable. |
| `fraud-service` | Scores transaction risk using trust, velocity, amount, and compliance signals before payment approval. | Cuts fraud losses and reduces false declines that frustrate legitimate customers. |
| `compliance-service` | Screens identities and transactions against PEP, sanctions, and AML requirements. | Helps the business stay bankable, compliant, and ready for regulated growth. |
| `event-core` | Standardizes domain events and outbox delivery across the platform. | Makes integrations reliable and keeps downstream audit and automation systems consistent. |
| `voice-service` | Provides voice enrollment, verification, liveness, replay detection, and fallback selection. | Gives users a low-friction authentication path and reduces password-reset pressure. |
| `support-service` | Gives support teams search, freeze, dispute, and repair workflows tied to ledger truth. | Shortens resolution time and gives the company traceable, defensible support actions. |
| `recovery-service` | Rebuilds trust after compromise through document upload, video KYC, reenrollment, and certificate reissue. | Lets legitimate users regain access safely instead of abandoning the account. |
| `ops-service` | Models telemetry, dashboards, alerts, release gates, and disaster recovery requirements. | Improves uptime, incident response, and leadership visibility into operational risk. |
| `launch-service` | Validates chaos, pen test, security, performance, and fallback gates before release. | Prevents unsafe releases and protects reputation, revenue, and customer trust. |

## Current Slice

- Java 17 `ledger-service` domain model.
- Java 17 `payment-service` saga core.
- Java 17 `identity-service`, `compliance-service`, and `fraud-service` cores.
- Shared event backbone with in-memory outbox relay.
- Python `voice-service` biometrics core.
- Java 17 `support-service` and `recovery-service` cores for support workflows.
- Java 17 `ops-service` plan validator for observability and disaster recovery.
- Java 17 `launch-service` readiness validator for hardening and launch gates.
- PostgreSQL schema migration for signed, append-only ledger entries.
- In-memory repository for deterministic local tests.
- Repair API domain stub requiring a justification payload.
- Lightweight Java test runner covering reconciliation, idempotency, repairs,
  concurrent overdraft prevention, payment saga transitions, trust-layer
  checks, event-envelope behavior, recovery flows, support workflows,
  observability/DR plan validation, launch readiness, and voice verification
  flows.

## Run Tests

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-services.ps1
```
