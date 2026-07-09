package com.voicesecure.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class RefreshTokenFamilyState {
    private final UUID familyId;
    private final UUID userId;
    private final UUID deviceId;
    private final String scope;
    private final Duration accessTokenTtl;
    private String currentRefreshToken;
    private String previousRefreshToken;
    private boolean revoked;
    private Duration refreshTokenTtl;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant revokedAt;
    private Instant expiresAt;

    private RefreshTokenFamilyState(
            UUID familyId,
            UUID userId,
            UUID deviceId,
            String scope,
            Duration accessTokenTtl,
            String currentRefreshToken,
            Duration refreshTokenTtl,
            Instant createdAt
    ) {
        this.familyId = familyId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.scope = scope == null ? "" : scope.trim();
        if (this.scope.isBlank()) {
            throw new IdentityException("scope is required");
        }
        this.accessTokenTtl = accessTokenTtl;
        this.currentRefreshToken = currentRefreshToken;
        this.refreshTokenTtl = refreshTokenTtl;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = createdAt.plus(refreshTokenTtl);
    }

    public static RefreshTokenFamilyState create(UUID userId, UUID deviceId, String scope, Duration accessTokenTtl, Duration refreshTokenTtl) {
        Instant now = Instant.now();
        return new RefreshTokenFamilyState(UUID.randomUUID(), userId, deviceId, scope, accessTokenTtl, randomToken(), refreshTokenTtl, now);
    }

    public UUID familyId() {
        return familyId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID deviceId() {
        return deviceId;
    }

    public String scope() {
        return scope;
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public String currentRefreshToken() {
        return currentRefreshToken;
    }

    public boolean revoked() {
        return revoked;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public RefreshTokenFamilyState copy() {
        RefreshTokenFamilyState copy = new RefreshTokenFamilyState(
                familyId,
                userId,
                deviceId,
                scope,
                accessTokenTtl,
                currentRefreshToken,
                refreshTokenTtl,
                createdAt
        );
        copy.previousRefreshToken = previousRefreshToken;
        copy.revoked = revoked;
        copy.updatedAt = updatedAt;
        copy.revokedAt = revokedAt;
        copy.expiresAt = expiresAt;
        return copy;
    }

    public RefreshTokenFamilyState snapshot() {
        return copy();
    }

    public RefreshTokenRotation rotate(String presentedRefreshToken, Duration ttl) {
        if (revoked) {
            throw new IdentityException("refresh token family is revoked");
        }
        if (Instant.now().isAfter(expiresAt)) {
            revoke("refresh token family expired");
            throw new IdentityException("refresh token family expired");
        }
        if (currentRefreshToken.equals(presentedRefreshToken)) {
            previousRefreshToken = currentRefreshToken;
            currentRefreshToken = randomToken();
            updatedAt = Instant.now();
            refreshTokenTtl = ttl;
            expiresAt = updatedAt.plus(ttl);
            return new RefreshTokenRotation(currentRefreshToken, previousRefreshToken);
        }
        if (presentedRefreshToken != null && presentedRefreshToken.equals(previousRefreshToken)) {
            revoke("refresh token reuse detected");
            throw new IdentityException("refresh token reuse detected");
        }
        revoke("unknown refresh token");
        throw new IdentityException("refresh token not recognized");
    }

    private void revoke(String reason) {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.updatedAt = this.revokedAt;
    }

    private static String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
