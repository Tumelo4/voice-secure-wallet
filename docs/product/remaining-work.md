# Remaining work

This is the ownership handoff for work that cannot be completed from the
repository alone. Repository implementation is current through PR #88. The
authoritative capability claims remain in
[`capabilities.yaml`](capabilities.yaml) and the generated
[`current-capability-status.md`](current-capability-status.md).

## Working rules

- Do not promote a capability based only on code or disposable-test evidence.
- Store immutable run identifiers, timestamps, approvers, and evidence links.
- Treat failed gates as release blockers; do not rewrite them as accepted risk
  without an accountable owner and expiry date.
- Update `capabilities.yaml`, regenerate the status document, and link the
  external evidence when a gate is completed.

## Now — establish the managed staging baseline

| Work | Accountable owner | Completion evidence |
| --- | --- | --- |
| Apply the production-reference Terraform in an approved AWS staging account. | Platform | Plan/apply logs, immutable revision, resource inventory, cost baseline, and teardown procedure. |
| Validate MSK topics, including `notifications.dlq`, replication, authentication, encryption, producer acknowledgement, consumer restart, and DLQ replay. | Payments platform | Managed-cluster test report with broker/topic configuration, failure injection, offsets, and replay results. |
| Validate RDS migrations, backup/restore, connection exhaustion, failover, and multi-instance payment/notification behavior. | Platform + payments | Migration log, failover timeline, reconciliation result, measured RPO/RTO, and clean restore evidence. |
| Validate ElastiCache TLS/auth, shared rate-limit budgets, outage behavior, recovery, and exported metrics. | API platform | Configuration capture, two-instance load result, outage trace, dashboards, and alert delivery evidence. |
| Validate KMS envelope encryption, key policy, rotation, denial behavior, and audited voice revoke/delete operations. | Voice security + platform | KMS/RDS test record, CloudTrail references, rotation result, and lifecycle audit extracts. |

Use [`aws-staging-deployment.md`](../operations/aws-staging-deployment.md),
[`aws-staging-deployment.sh`](../../scripts/aws-staging-deployment.sh), and the
[`production-reference`](../../infra/aws/environments/production-reference)
configuration as the starting point.

## Next — prove customer and operational behavior

| Work | Accountable owner | Completion evidence |
| --- | --- | --- |
| Run physical-device voice capture across supported OS/device/codec combinations, poor networks, permission denial, cancellation, and accessibility paths. | Mobile | Device matrix, recordings metadata without biometric content, accessibility report, and pass/fail results. |
| Commission independent biometric accuracy, liveness, replay, and spoof evaluation against agreed thresholds and representative cohorts. | Voice security | Signed assessor report, dataset governance record, thresholds, error rates, open findings, and named approval. |
| Exercise voice fallback settlement end to end against managed dependencies, including timeout, OTP fallback, duplicate callbacks, restart, and reconciliation. | Payments + voice security | Trace-linked saga/ledger/outbox records proving one balanced settlement or explicit manual review. |
| Run multi-instance fault injection for API, payment recovery, outbox relays, notification consumers, Redis, Kafka, and database failover. | Platform + service owners | Chaos run IDs, customer-impact summary, invariants, recovery times, and tracked defects. |
| Establish live dashboards, four-golden-signal alerts, paging routes, and a sustained staging soak. | Operations | Dashboard URLs, alert tests, SLO history, incident log, and soak report. |

Record phase-three and phase-four results using
[`phase3-evidence-manifest.example.json`](../operations/phase3-evidence-manifest.example.json)
and
[`phase4-evidence-manifest.example.json`](../operations/phase4-evidence-manifest.example.json),
then run their matching validators in `scripts/`.

## Later — independent launch gates

| Work | Accountable owner | Completion evidence |
| --- | --- | --- |
| Complete penetration testing and remediate all critical/high findings. | Security | Independent report, remediation references, retest result, and risk-owner sign-off. |
| Complete PCI/privacy/compliance assessment for the deployed scope and operating model. | Compliance | Signed assessment, scope statement, control evidence, exceptions, and expiry dates. |
| Execute disaster-recovery and regional/cutover rollback drills. | Platform + operations | Measured RPO/RTO, reconciliation evidence, rollback duration, defects, and approved drill report. |
| Approve release, perform digest-pinned cutover, and complete one week of monitored operation. | Release owners | Five named sign-offs, change ticket, release digest, SLO history, incident log, and first-week review. |

Use [`disaster-recovery.md`](../operations/disaster-recovery.md),
[`release-runbook.md`](../release-runbook.md), and
[`phase5-evidence-manifest.example.json`](../operations/phase5-evidence-manifest.example.json).
Production readiness remains blocked until the non-example manifests pass the
phase-three, phase-four, and phase-five validators.

## Explicitly deferred

Restoring push and pull-request CI triggers is intentionally outside the
current implementation sequence because those workflows take material time to
complete. Before restoration, confirm runner capacity, expected duration,
branch-protection requirements, and cancellation/concurrency behavior. Restore
the triggers in a dedicated PR and observe one complete successful run before
making the checks required.

## Exit condition

The remaining program is complete only when every row above has immutable
evidence, an accountable approval, and no unresolved release-blocking finding;
`capabilities.yaml` and `current-capability-status.md` must then reflect the
same facts.
