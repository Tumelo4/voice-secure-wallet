# ops-service

Observability and disaster recovery plan model for VoiceSecure Wallet.

## Problem Statement

Operational safety cannot live only in slide decks. The team needs an
executable model for telemetry, alerting, SLOs, release gates, and disaster
recovery so readiness can be validated before a real incident or outage.

## Impact

- Operators get clearer signals during incidents and faster paths to diagnosis.
- Leadership gets a concrete view of release and recovery readiness.
- The business reduces downtime risk and improves confidence in production
  changes.

## Scope

This service captures the phase 6 operational requirements as executable plan
validation: telemetry shape, four golden signals, SLO dashboards, alert tiers,
the five-stage release pipeline, reconciliation cadence, and disaster recovery
restore gates.

## Current Guarantees

- Structured telemetry requires the expected fields and trace headers.
- SLO dashboards and alert tiers are modeled explicitly.
- Disaster recovery plans require restore test coverage before release readiness.
- Required dashboards, release stages, log fields, and reconciliation cadence are
  configured through `OpsReadinessPolicy`.

## Benchmark

- Every critical service in `OpsReadinessPolicy.requiredDashboardServices()`
  must have an SLO dashboard.
- Restore-test readiness should capture RTO/RPO evidence in the DR spec notes.
- Reconciliation cadence must match the policy target, defaulting to every 6
  hours.
- Alert coverage must include Tier 1, Tier 2, and Tier 3, with runbooks for all
  Tier 1 alerts.

## How To Use It

Construct an operations plan and validate it against a policy:

```java
OpsReadinessPolicy policy = OpsReadinessPolicy.defaults();
OpsPlanValidator validator = new OpsPlanValidator(policy);
OpsPlanValidationReport report = validator.validate(plan);
```

Use `report.blockers()` as the release-readiness checklist for observability,
deployment, and disaster recovery gaps.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
