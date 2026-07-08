# acceptance-tests

Executable BDD acceptance scenarios for VoiceSecure Wallet.

## Problem Statement

Cross-service flows can look correct in isolated unit tests while still failing
when payment, fraud, compliance, ledger, notification, and wallet behavior are
composed together. The team needs scenario tests that prove the business
journey end to end.

## Impact

- Product owners get a readable proof of the customer journey.
- Engineers get an early signal when a service boundary or integration contract
  breaks a user-visible flow.
- The business reduces release risk from cross-context regressions.

## Scope

This slice validates the three current BDD scenarios: voice fallback moving a
payment forward, compliance hits blocking movement of funds, and wallet
projections following ledger truth.

## Current Guarantees

- Voice fallback can complete a legitimate payment.
- Compliance hits block funds movement before ledger mutation.
- Wallet projections remain aligned with ledger entries.

## Benchmark

- 3 acceptance scenario tests pass.

## How To Use It

Run the cross-service verification runner from this module directory:

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-verification.ps1
```
