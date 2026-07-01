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

## Pre-Release Checklist

- [ ] The current branch includes the full implementation stack and the latest
  README updates.
- [ ] The ubiquitous language doc reflects any new bounded contexts or domain
  terms introduced by the release.
- [ ] `powershell -ExecutionPolicy Bypass -File .\scripts\test-services.ps1`
  passes on a clean working tree.
- [ ] The `Service CI` GitHub Actions workflow passes for the release PR,
  including local event contract tests and web dashboard tests.
- [ ] Open pull requests are reviewed in stack order.
- [ ] No service README is missing a problem statement or impact section.
- [ ] Release notes call out user-visible and company-facing impact.
- [ ] Operations and support teams know the rollout window and escalation path.

## Rollout Checklist

- [ ] Confirm the release branch to merge and the target environment.
- [ ] Verify staging smoke tests for payment, voice auth, support search, and
  recovery flow.
- [ ] Confirm dashboards are live before traffic moves.
- [ ] Confirm alert routing is working for payment, identity, and ops signals.
- [ ] Confirm disaster recovery evidence is attached for the latest run.
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
