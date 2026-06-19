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

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
