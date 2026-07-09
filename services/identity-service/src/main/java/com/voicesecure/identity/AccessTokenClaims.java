package com.voicesecure.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccessTokenClaims(
        UUID subjectUserId,
        UUID deviceId,
        UUID familyId,
        String scope,
        UUID tokenId,
        Instant issuedAt,
        Instant expiresAt
) {
    public AccessTokenClaims {
        Objects.requireNonNull(subjectUserId, "subjectUserId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}

