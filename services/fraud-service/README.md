# fraud-service

Java 17 fraud core for VoiceSecure Wallet.

## Problem Statement

Payment authorization needs to react to risk in real time. If fraud signals are
scattered or delayed, the platform either lets bad transactions through or
blocks good customers without enough context.

## Impact

- Users see smarter approvals with fewer false declines.
- Risk teams get a single policy surface for velocity, trust, compliance, and
  amount-based decisions.
- The business reduces fraud loss while protecting conversion and customer
  confidence.

## Scope

This service combines compliance screening, velocity tracking, device trust, and
transaction amount signals into an authorization policy for the payment saga.

## Benchmark

- Fraud evaluation should stay under 10 ms per request with the in-memory
  compliance adapter.
- Velocity scoring should remain deterministic inside the configured policy
  window.
- Policy changes should be possible through `FraudPolicy` without changing
  `FraudService`.

## How To Use It

Create a compliance port, policy, and service, then evaluate a transaction:

```java
ComplianceScreeningPort compliance = new ComplianceServiceScreeningPort(complianceService);
FraudService fraud = new FraudService(compliance, new VelocityTracker(), FraudPolicy.defaults());
FraudAssessment assessment = fraud.evaluate(request);
```

Use `assessment.authPolicy()` and `assessment.voiceThreshold()` to drive the
payment and voice-verification paths.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
