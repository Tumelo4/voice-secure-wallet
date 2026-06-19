# identity-service

Java 17 identity core for VoiceSecure Wallet.

## Problem Statement

The platform needs a reliable way to establish who the user is, which device
they are using, and whether a request should be trusted enough to continue.
Without that foundation, fraud controls and recovery flows lose their footing.

## Impact

- Users can register devices, authenticate, and recover access with fewer dead
  ends.
- Security teams get a stronger control point for session and token lifecycle
  management.
- The business reduces account takeover exposure and improves trust in every
  downstream authorization decision.

## Scope

This slice handles device registration, RSA JWT issuance and verification,
refresh token family rotation, token-family revocation, and device signature
validation for critical requests.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
