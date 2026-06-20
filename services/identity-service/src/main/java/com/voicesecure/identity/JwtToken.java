package com.voicesecure.identity;

import java.util.Objects;

public record JwtToken(String token, AccessTokenClaims claims) {
    public JwtToken {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(claims, "claims");
    }
}
