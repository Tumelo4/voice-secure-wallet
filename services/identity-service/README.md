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

This slice handles device registration, Nimbus-backed RSA JWT issuance and verification,
refresh token family rotation, token-family revocation, and device signature
validation for critical requests. Refresh rotation now preserves the access
token scope so renewed credentials do not silently drop permissions.

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

## Signing-key rotation

1. Generate the next RSA key pair and publish its public key in the accepted-key registry/JWKS.
2. Make the new key the current signing key while retaining the previous public key.
3. Wait at least the maximum access-token TTL plus clock-skew allowance.
4. Remove the previous public key; tokens carrying its retired `kid` are then rejected.

The overlap and retirement behaviors, multi-key JWKS, malformed tokens, complex
Unicode claims and algorithm/`kid` pinning are adversarially tested.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```
