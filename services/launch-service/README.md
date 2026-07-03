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
checks, fallback volume, performance, production cutover, and the remaining
sign-off criteria.

## Current Guarantees

- All modeled launch gates are represented explicitly, including production
  cutover evidence.
- Chaos testing and fallback thresholds are enforced.
- Security scanning, pen testing, and launch readiness must all be clean before
  the plan reports GO.
- Launch thresholds are configured through `LaunchReadinessPolicy`.
- Benchmark evidence is modeled separately from pass/fail assertions.
- Production cutover evidence must link the change ticket, tested rollback,
  locked feature flags, armed monitoring, on-call coverage, support briefing,
  and rollback timing.

## Benchmark

- Voice shadow mode must run for 48 hours with a false-positive rate below
  0.1%.
- Performance testing must reach 10x load and keep p99 latency within SLO.
- Voice OTP fallback must complete 100 of 100 intentionally degraded test
  payments.
- Launch evidence must include a test run id, measured p99 latency, load
  multiplier, false-positive sample size, RTO/RPO minutes, CVE scan source, and
  pen-test report reference.
- Production rollback must be executable within the default 30-minute policy
  threshold.

## How To Use It

Build a readiness plan with policy evidence, then validate it:

```java
LaunchReadinessPolicy policy = LaunchReadinessPolicy.defaults();
LaunchReadinessValidator validator = new LaunchReadinessValidator(policy);
LaunchReadinessReport report = validator.validate(plan);
```

Use `report.blockers()` as the go/no-go list. A ready report means every gate
and the supporting benchmark evidence satisfy the policy.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
