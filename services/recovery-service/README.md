# recovery-service

Recovery workflow core for VoiceSecure Wallet.

This service coordinates identity document upload, video KYC approval, voice
reenrollment, device certificate reissue, and the final recovery completion
event.

## Current Guarantees

- Recovery cannot complete until the required steps have been satisfied.
- Device certificate reissue is delegated to the identity service.
- Recovery completion emits a `recovery.completed` event.

