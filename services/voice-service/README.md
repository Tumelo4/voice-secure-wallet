# voice-service

Python voice-verification core for VoiceSecure Wallet.

## Problem Statement

The platform needs a high-signal, low-friction way to verify a user without
forcing them through passwords and repeated manual checks. Voice verification
has to be strong enough to be useful and resilient enough to support fallback.

## Impact

- Users get a faster authentication path with less friction.
- Support teams see fewer routine verification calls and password resets.
- The business gains a distinctive, secure control that supports conversion and
  reduces account abuse.

## Scope

This slice implements enrollment, challenge issuance, liveness scoring,
replay detection, and fallback selection.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
