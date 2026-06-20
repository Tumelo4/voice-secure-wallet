# identity-service

Java 17 identity core for VoiceSecure Wallet.

This slice handles device registration, RSA JWT issuance and verification,
refresh token family rotation, token-family revocation, and device signature
validation for critical requests.

## Benchmark

- Access-token verification should stay under 5 ms per token locally.
- Refresh-token rotation should revoke a reused token family deterministically.
- Critical request signature verification should reject invalid device
  signatures without calling downstream payment or recovery logic.

## How To Use It

Register a device, create a session, and verify the issued access token:

```java
IdentityService identity = new IdentityService(repository, signingKeyPair, "voice-secure-key-1");
identity.registerDevice(userId, deviceId, devicePublicKey);
SessionGrant grant = identity.createSession(userId, deviceId, "wallet:payment", accessTtl, refreshTtl);
AccessTokenClaims claims = identity.verifyAccessToken(grant.accessToken().token());
```

Use `rotateRefreshToken` for refresh-token rotation and
`validateCriticalRequest` for device-bound request signatures.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

