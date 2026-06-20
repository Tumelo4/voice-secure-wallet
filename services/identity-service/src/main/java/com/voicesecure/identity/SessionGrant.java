package com.voicesecure.identity;

import java.util.Objects;

public record SessionGrant(JwtToken accessToken, String refreshToken, AccessTokenClaims claims, RefreshTokenFamilyState familyState) {
    public SessionGrant {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(familyState, "familyState");
    }
}
