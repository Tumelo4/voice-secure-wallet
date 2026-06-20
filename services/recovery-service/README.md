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
- Voice reenrollment and device certificate reissue are single-transition
  recovery steps.

## Benchmark

- Recovery completion should stay under 50 ms locally after prerequisites are
  satisfied.
- Duplicate reenrollment and certificate reissue attempts should fail before
  calling external ports.
- The completion event should be emitted exactly once per completed recovery.

## How To Use It

Start recovery, collect evidence, perform trust rebuilding, then complete:

```java
RecoveryService recovery = new RecoveryService(repository, deviceCertificatePort, voiceReenrollmentPort, eventPublisher);
RecoveryCase recoveryCase = recovery.startRecovery(userId);
recovery.uploadIdentityDocument(recoveryCase.recoveryId(), "passport", checksum);
recovery.completeVideoKyc(recoveryCase.recoveryId(), true);
recovery.requestVoiceReenrollment(recoveryCase.recoveryId());
recovery.reissueDeviceCertificate(recoveryCase.recoveryId(), deviceId, publicKey);
recovery.completeRecovery(recoveryCase.recoveryId(), traceId);
```

Use `DeviceCertificatePort` and `VoiceReenrollmentPort` to keep recovery
workflow policy separate from identity and voice implementations.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
