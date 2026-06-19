# launch-service

Launch hardening and readiness model for VoiceSecure Wallet.

## Problem Statement

A release is only safe when the team can prove the hardening work is complete.
Without launch gates, outages and security gaps reach production before they are
properly challenged.

## Impact

- Users get a more stable release with fewer surprises at launch.
- Engineering and security teams have a clear go/no-go standard.
- The business protects its reputation, revenue, and customer trust at the most
  sensitive point in the delivery cycle.

## Scope

This service captures the phase 7 launch gates as an executable readiness
validator: chaos testing, pen testing, security scanning, shadow-mode voice
checks, fallback volume, performance, and the remaining sign-off criteria.

## Current Guarantees

- All 16 launch gates are represented explicitly.
- Chaos testing and fallback thresholds are enforced.
- Security scanning, pen testing, and launch readiness must all be clean before
  the plan reports GO.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
