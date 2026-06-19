# recovery-service

Recovery workflow core for VoiceSecure Wallet.

## Problem Statement

Account recovery has to restore legitimate access without reopening the door to
fraud. If the process is too loose, attackers get back in; if it is too strict,
customers abandon the account.

## Impact

- Users can recover access through a controlled, traceable path.
- Security and support teams get a safer alternative to ad hoc manual resets.
- The business preserves customer trust while limiting recovery abuse.

## Scope

This service coordinates identity document upload, video KYC approval, voice
reenrollment, device certificate reissue, and the final recovery completion
event.

## Current Guarantees

- Recovery cannot complete until the required steps have been satisfied.
- Device certificate reissue is delegated to the identity service.
- Recovery completion emits a `recovery.completed` event.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
