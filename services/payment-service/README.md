# payment-service

Java 17 saga orchestrator for VoiceSecure Wallet.

This service coordinates fraud approval, voice verification, fallback handling,
funds reservation, ledger commit, completion, and compensation. The initial
implementation is in-memory so the state machine can be tested without any
infrastructure.

## Current Guarantees

- Idempotent saga start by `idempotencyKey`.
- Explicit state transitions for the full payment lifecycle.
- Voice failure can route to fallback when the auth policy permits it.
- Compensation is first-class and emits audit-friendly events.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

