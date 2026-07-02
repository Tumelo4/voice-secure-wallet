# Ubiquitous Language

This glossary keeps the VoiceSecure Wallet code, tests, and PDF plan aligned.
Use these words consistently in commits, PRs, tests, READMEs, and future HTTP or
infrastructure adapters.

## Core Principles

- The ledger is the financial authority.
- Wallet balances are projections of ledger truth.
- Voice is an authentication factor, not the only path to payment completion.
- Compliance blocks are business decisions, not technical failures.
- Compensation failure is a human-in-the-loop incident.

## Bounded Contexts

| Context | Service | Owns | Does Not Own |
| --- | --- | --- | --- |
| Ledger | `ledger-service` | Signed entries, account balances, reconciliation, repair entries. | User-facing balance UI, payment decisions, support case lifecycle. |
| Wallet | `wallet-service` | Wallet metadata and read-model projections from ledger events. | Ledger writes or financial correction. |
| Payment | `payment-service` | Saga state, idempotency, auth/fallback progression, compensation state. | Fraud scoring, biometric matching, ledger internals, notification delivery. |
| Fraud | `fraud-service` | Risk score, auth policy, voice threshold, velocity/device trust decisions. | Regulatory audit ownership and payment state changes. |
| Compliance | `compliance-service` | PEP, sanctions, AML, STR evidence, compliance audit trail. | Fraud scoring formulas and saga orchestration. |
| Identity | `identity-service` | RS256 tokens, refresh-token families, device public keys, device signatures. | Recovery KYC workflow and payment authorization decisions. |
| Voice | `voice-service` | Enrollment, challenge/liveness checks, replay detection, confidence scoring. | OTP delivery and fraud policy selection. |
| Notification | `notification-service` | Receipts, failure notices, compensation notices, OTP fallback delivery. | Payment progression and voice verification decisions. |
| Support | `support-service` | Disputes, freezes, support audit log, repair escalation workflow. | Direct ledger mutation outside the repair port. |
| Recovery | `recovery-service` | ID/video KYC recovery, voice reenrollment request, device certificate reissue. | Primary identity session lifecycle. |
| Operations | `ops-service` | SLO/alert/runbook/release/DR readiness policy. | Runtime alert delivery infrastructure. |
| Launch | `launch-service` | Launch gate evidence and go/no-go validation. | Producing the evidence itself. |
| API Adapter | `api-adapter-service` | HTTP-style request validation, route selection, response shaping, error mapping, runtime auth, trace, rate-limit, and request-log guards. | Payment, ledger, wallet, fraud, or identity business decisions. |

`event-core` is shared infrastructure for domain events and outbox behavior. It
is not a bounded context with business ownership.

`apps/mobile` is the React Native readiness dashboard. It is a presentation
surface over build evidence, not a bounded context that owns payment, ledger,
risk, or launch decisions.

`api-adapter-service` is a boundary adapter. It protects domain services from
HTTP, JSON, authentication, traceability, rate-limit, and request-log details,
but it does not own business policy.

## Terms

| Term | Meaning |
| --- | --- |
| Signed ledger entry | A ledger entry with `signedAmount`; negative values debit, positive values credit. |
| Reconciliation | The proof that all signed ledger entries sum to zero. |
| Repair | A compensating ledger action with justification, audit trail, and support/SRE ownership. |
| Wallet projection | A read model built from ledger events for user-facing balance display. |
| Payment saga | The explicit lifecycle that moves a payment through fraud, auth, funds movement, completion, and compensation. |
| Auth policy | Fraud-selected authentication requirement: `VOICE_ONLY`, `VOICE_OTP`, or `DEVICE_PIN`. |
| Voice fallback | The path from voice timeout/rejection into OTP/PIN instead of blocking a legitimate customer. |
| Compliance hit | A PEP, sanctions, or AML result that blocks payment before funds move. |
| Fraud scored event | Contract event emitted by the fraud context with score, approval, auth policy, voice threshold, and device/velocity evidence. |
| Compliance hit event | Contract event emitted only for blocking PEP, sanctions, or AML screening results. |
| Device binding | A registered device key used to sign critical requests. |
| Token family revocation | Revocation of all refresh tokens in a family after reuse is detected. |
| Launch evidence | Measured proof for reconciliation, chaos, security, shadow mode, performance, DR, and documentation gates. |
| Readiness dashboard | UI surface that summarizes service slices, tests, phase status, and remaining production blockers. |
| Mobile UI stack | React Native TypeScript app surface styled with NativeWind/Tailwind CSS and backed by Redux Toolkit state. |
| Mobile API client | TypeScript boundary that sends API runtime headers, maps payment and wallet responses, preserves API errors, and feeds Redux-friendly request state. |
| API adapter | Boundary layer that translates HTTP-style requests into domain service calls and maps domain outcomes back to stable JSON responses. |
| API runtime boundary | Guard layer that verifies bearer tokens, requires trace IDs, rate-limits authenticated principals, forwards valid requests, and records request outcomes. |

## Testing Style

| Style | How We Use It |
| --- | --- |
| TDD | Add failing tests first for new behavior, then implement the smallest green slice, then refactor. |
| BDD | Write cross-context scenarios in product language using `Given/When/Then` style test names. |
| DDD | Keep each service responsible for its bounded context and connect contexts through ports, events, or explicit adapters. |

## Current BDD Scenarios

- Scenario: voice fallback keeps a legitimate payment moving.
- Scenario: compliance hit blocks funds movement.
- Scenario: wallet read model follows ledger truth.

These scenarios live in
`services/acceptance-tests/src/test/java/com/voicesecure/acceptance`.

## Current Contract Tests

- Contract: `fraud.scored` carries auth policy and threshold.
- Contract: `compliance.hit` carries PEP hit evidence.
- Contract: clear compliance results cannot publish `compliance.hit`.

These local contract tests live in
`services/contract-tests/src/test/java/com/voicesecure/contracts`. They are the
domain-level stepping stone before Pact and Schema Registry checks are wired
into CI.
