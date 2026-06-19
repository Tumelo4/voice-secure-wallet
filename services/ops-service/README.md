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

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
