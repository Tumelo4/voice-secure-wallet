package com.voicesecure.identity;

import java.time.Duration;
import java.util.UUID;

public final class RefreshTokenRotationTests {
    public static void main(String[] args) {
        RefreshTokenFamilyState family = RefreshTokenFamilyState.create(
                UUID.randomUUID(), UUID.randomUUID(), "wallet:pay", Duration.ofMinutes(15), Duration.ofDays(7));
        String first = family.currentRefreshToken();
        RefreshTokenRotation rotation = family.rotate(first, Duration.ofDays(7));
        if (rotation.refreshToken().equals(first)) throw new AssertionError("rotation must replace the token");
        try {
            family.rotate(first, Duration.ofDays(7));
            throw new AssertionError("refresh-token reuse must be rejected");
        } catch (IdentityException expected) {
            if (!family.snapshot().revoked()) throw new AssertionError("reuse must revoke the token family");
        }
        System.out.println("Refresh token rotation tests passed: 2");
    }
}
