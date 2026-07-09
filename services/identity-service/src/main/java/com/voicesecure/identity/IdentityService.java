package com.voicesecure.identity;

import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class IdentityService {
    private final IdentityRepository repository;
    private final JwtCodec jwtCodec;

    public IdentityService(IdentityRepository repository, KeyPair signingKeyPair, String keyId) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.jwtCodec = new JwtCodec(signingKeyPair, keyId);
    }

    public DeviceRegistration registerDevice(UUID userId, UUID deviceId, PublicKey publicKey) {
        DeviceRegistration registration = new DeviceRegistration(userId, deviceId, publicKey, Instant.now(), true);
        repository.saveDevice(registration);
        return registration;
    }

    public DeviceRegistration reissueDeviceCertificate(UUID userId, UUID deviceId, PublicKey publicKey) {
        repository.findDevice(userId, deviceId)
                .orElseThrow(() -> new IdentityException("device not registered"));
        DeviceRegistration registration = new DeviceRegistration(userId, deviceId, publicKey, Instant.now(), true);
        repository.saveDevice(registration);
        return registration;
    }

    public SessionGrant createSession(UUID userId, UUID deviceId, String scope, Duration accessTokenTtl, Duration refreshTokenTtl) {
        DeviceRegistration device = repository.findDevice(userId, deviceId)
                .orElseThrow(() -> new IdentityException("device not registered"));
        if (!device.active()) {
            throw new IdentityException("device is inactive");
        }

        String normalizedScope = normalizeScope(scope);
        RefreshTokenFamilyState family = RefreshTokenFamilyState.create(userId, deviceId, normalizedScope, accessTokenTtl, refreshTokenTtl);
        repository.saveFamily(family);
        AccessTokenClaims claims = new AccessTokenClaims(
                userId,
                deviceId,
                family.familyId(),
                normalizedScope,
                UUID.randomUUID(),
                Instant.now(),
                Instant.now().plus(accessTokenTtl)
        );
        return new SessionGrant(jwtCodec.issue(claims), family.currentRefreshToken(), claims, family.snapshot());
    }

    public AccessTokenClaims verifyAccessToken(String token) {
        return jwtCodec.verify(token);
    }

    public SessionGrant rotateRefreshToken(UUID familyId, String presentedRefreshToken, Duration refreshTokenTtl) {
        RefreshTokenFamilyState family = repository.findFamily(familyId)
                .orElseThrow(() -> new IdentityException("refresh token family not found"));
        RefreshTokenRotation rotation = family.rotate(presentedRefreshToken, refreshTokenTtl);
        repository.saveFamily(family);
        AccessTokenClaims claims = new AccessTokenClaims(
                family.userId(),
                family.deviceId(),
                family.familyId(),
                family.scope(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now().plus(family.accessTokenTtl())
        );
        return new SessionGrant(jwtCodec.issue(claims), rotation.refreshToken(), claims, family.snapshot());
    }

    public boolean validateCriticalRequest(UUID userId, UUID deviceId, byte[] payload, byte[] signature) {
        DeviceRegistration device = repository.findDevice(userId, deviceId)
                .orElseThrow(() -> new IdentityException("device not registered"));
        return JwtCodec.verifySignature(device.publicKey(), payload, signature);
    }

    public JwksDocument jwks() {
        return new JwksDocument(List.of(jwtCodec.publicJwk()));
    }

    private static String normalizeScope(String scope) {
        if (scope == null) {
            throw new IdentityException("scope is required");
        }
        String normalizedScope = scope.trim();
        if (normalizedScope.isBlank()) {
            throw new IdentityException("scope is required");
        }
        return normalizedScope;
    }
}
