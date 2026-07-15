package com.voicesecure.identity;

import java.time.Duration;
import java.util.UUID;

public final class RefreshTokenRotationTests {
    public static void main(String[] args) {
        RefreshTokenFamilyState family = RefreshTokenFamilyState.create(
                UUID.randomUUID(), UUID.randomUUID(), "wallet:pay", Duration.ofMinutes(15), Duration.ofDays(7));
        if (family.revoked()) throw new AssertionError("new token family must be active");
        if (family.createdAt() == null || family.updatedAt() == null || family.expiresAt() == null) {
            throw new AssertionError("token family timestamps must be populated");
        }
        if (family.revokedAt() != null) throw new AssertionError("active token family cannot have a revocation time");
        String first = family.currentRefreshToken();
        RefreshTokenRotation rotation = family.rotate(first, Duration.ofDays(7));
        if (rotation.refreshToken().equals(first)) throw new AssertionError("rotation must replace the token");
        try {
            family.rotate(first, Duration.ofDays(7));
            throw new AssertionError("refresh-token reuse must be rejected");
        } catch (IdentityException expected) {
            if (!family.snapshot().revoked()) throw new AssertionError("reuse must revoke the token family");
        }
        RefreshTokenFamilyState expired = RefreshTokenFamilyState.create(
                UUID.randomUUID(), UUID.randomUUID(), "wallet:pay", Duration.ofMinutes(15), Duration.ofSeconds(-1));
        try {
            expired.rotate(expired.currentRefreshToken(), Duration.ofDays(7));
            throw new AssertionError("expired refresh-token family must be rejected");
        } catch (IdentityException expected) {
            if (!expired.revoked() || expired.revokedAt() == null) {
                throw new AssertionError("expiry must revoke the token family");
            }
        }
        System.out.println("Refresh token rotation tests passed: 3");
    }
}
