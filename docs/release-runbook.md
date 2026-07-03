# Release Runbook

This runbook is the non-code release checklist for VoiceSecure Wallet. It
captures the steps the team should follow when moving the platform from a
completed implementation branch to a production release.

## Problem Statement

A fintech release is only safe when engineering, security, operations, support,
and recovery are all ready at the same time. Without a shared checklist, the
team can ship code that passes unit tests but still leaves gaps in observability,
incident response, rollback, or customer recovery.

## Impact

- Users get a release that is more stable, supportable, and recoverable.
- The company reduces launch risk, incident time, and manual escalation work.
- Release owners can prove readiness with evidence instead of relying on memory.

## Phase Gate Summary

| Phase | What Must Be True | Evidence |
| --- | --- | --- |
| Phase 1: Ledger Core | Ledger writes are balanced, append-only, repair-backed, and projected into wallet balances. | `scripts/test-services.ps1`, ledger tests, and wallet projection tests. |
| Phase 2: Payment Saga | Payment orchestration is idempotent, compensating, and notification-decoupled. | Saga transition tests and notification consumer tests. |
| Phase 3: Identity, Fraud, Compliance | Identity, risk, screening gates, and fraud/compliance event contracts behave consistently. | Identity, fraud, compliance, and contract tests. |
| Phase 4: Event Backbone | Events share one contract and publish reliably. | Event envelope and relay tests. |
| Phase 5: Voice Biometrics | Enrollment, liveness, replay detection, fallback, and OTP notification boundary are covered. | Voice service tests and notification fallback tests. |
| Phase 6: Support and Recovery | Support search, freezes, disputes, and recovery workflows are traceable. | Support and recovery tests. |
| Phase 7: Observability and DR | Telemetry, SLOs, alerts, and restore gates are modeled. | Ops plan validator tests. |
| Phase 8: Hardening and Launch | Chaos, pen test, security scan, performance, and shadow mode are green. | Launch readiness tests. |
| Phase 9: API Adapters | HTTP-style payment commands and wallet balance reads map safely into domain services. | API adapter tests. |
| Phase 10: API Runtime Boundary | Auth, trace, rate-limit, and request-log guards wrap API adapters before a production server is added. | API runtime tests. |
| Phase 11: Mobile UI Stack | The readiness UI uses React Native, NativeWind/Tailwind CSS, and Redux Toolkit. | Mobile UI state-model tests. |
| Phase 12: Mobile TypeScript Hardening | Mobile UI model, selectors, Redux slice, and tests are TypeScript-first. | TypeScript mobile UI tests on Node 24. |
| Phase 13: Mobile API Client Boundary | Mobile payment and wallet reads use typed API clients, transport ports, and Redux-friendly request state. | Mobile API client tests. |
| Phase 14: Mobile Fetch Transport | React Native fetch is isolated behind `ApiTransport`, network failures are deterministic, and access tokens come from a provider port. | Mobile fetch transport tests. |
| Phase 15: Mobile Token Session | Token sessions are stored behind a vault port and refreshed before stale bearer tokens reach the API runtime. | Mobile token session tests. |
| Phase 16: Mobile Redux API Flows | Wallet-balance and payment-start commands dispatch loading, success, and failure branches into Redux request state. | Mobile Redux API flow tests. |
| Phase 17: Mobile Resilience Policy | Retryable mobile failures back off locally and offline payment commands queue idempotently until durable infrastructure is available. | Mobile resilience policy tests. |
| Phase 18: API Local HTTP Listener | JDK HTTP listener forwards real socket requests through API runtime guards without adding cloud infrastructure. | API HTTP server tests. |
| Phase 19: Durable Infrastructure Readiness | Kafka topic durability and AWS HA/encryption controls are executable preflight checks before live provisioning. | Durable infrastructure validator tests. |
| Phase 20: Terraform AWS Baseline | Terraform declares the first AWS baseline for VPC, KMS, MSK, RDS, Redis, S3 object lock, and managed secret references. | Terraform AWS baseline tests. |
| Phase 21: Production Cutover Readiness | Launch validation now blocks production without change approval, tested rollback, locked flags, armed monitoring, on-call coverage, support briefing, and rollback SLA evidence. | Production cutover launch tests. |
| Phase 22: Production Ingress Readiness | API ingress validation blocks production without TLS 1.3, mTLS, JWKS, distributed rate limits, WAF, HSTS, trace forwarding, body limits, and safe public paths. | Production ingress validator tests. |
| Phase 23: Mobile Native Secure Storage | Mobile token sessions now require encrypted, hardware-backed, device-only, biometric/passcode-protected storage that does not sync to cloud backups. | Mobile token session tests. |
| Phase 24: Mobile Screen Commands | Wallet-balance and payment-start forms validate user input before dispatching existing Redux API flows. | Mobile command form tests. |

## Pre-Release Checklist

- [ ] The current branch includes the full implementation stack and the latest
  README updates.
- [ ] The ubiquitous language doc reflects any new bounded contexts or domain
  terms introduced by the release.
- [ ] `powershell -ExecutionPolicy Bypass -File .\scripts\test-services.ps1`
  passes on a clean working tree.
- [ ] The `Service CI` GitHub Actions workflow passes for the release PR,
  including local event contract tests and mobile dashboard tests.
- [ ] Open pull requests are reviewed in stack order.
- [ ] No service README is missing a problem statement or impact section.
- [ ] Release notes call out user-visible and company-facing impact.
- [ ] Operations and support teams know the rollout window and escalation path.

## Rollout Checklist

- [ ] Confirm the release branch to merge and the target environment.
- [ ] Confirm the approved production change ticket is linked to the release.
- [ ] Confirm ingress TLS certificates, mTLS trust store, and DNS target are
  ready for the target environment.
- [ ] Verify staging smoke tests for payment, voice auth, support search, and
  recovery flow.
- [ ] Confirm feature flags are locked to the intended rollout path.
- [ ] Confirm dashboards are live before traffic moves.
- [ ] Confirm alert routing is working for payment, identity, and ops signals.
- [ ] Confirm WAF, HSTS, JWKS discovery, distributed rate limits, request body
  limits, and health-only public paths are enabled at the edge.
- [ ] Confirm iOS Keychain and Android Keystore builds use hardware-backed,
  device-only storage with biometric or passcode protection.
- [ ] Confirm token sessions are excluded from cloud backup/sync.
- [ ] Confirm wallet and payment command forms are wired to the production API
  client dependencies on real devices.
- [ ] Confirm primary and secondary on-call owners are online.
- [ ] Confirm support has the customer-facing briefing and escalation script.
- [ ] Confirm disaster recovery evidence is attached for the latest run.
- [ ] Confirm rollback has been tested and can complete within 30 minutes.
- [ ] Confirm launch gate status is GO before production promotion.

## Post-Release Checklist

- [ ] Monitor ledger reconciliation and payment completion rate.
- [ ] Watch fraud, compliance, and identity rejections for unexpected spikes.
- [ ] Confirm support can locate the new release in case volume increases.
- [ ] Confirm recovery workflows still complete end to end.
- [ ] Record any follow-up items, regressions, or deferred fixes.

## Rollback Checklist

- [ ] Stop promotion and freeze new deployment activity.
- [ ] Revert the last release artifact or merge if the issue is code-related.
- [ ] Disable traffic to the impacted path if partial release is in progress.
- [ ] Verify ledger integrity and payment state after rollback.
- [ ] Notify support and operations with the incident summary and next step.
- [ ] Capture the reason for rollback and the evidence that triggered it.

## Release Rule

If a critical test, operational check, or security gate fails, stop the release.
Do not promote on hope. Fix the issue, retest, and capture the evidence before
trying again.
