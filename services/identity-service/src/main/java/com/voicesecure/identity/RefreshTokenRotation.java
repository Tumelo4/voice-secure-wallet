package com.voicesecure.identity;

import java.util.Objects;

public record RefreshTokenRotation(String refreshToken, String previousRefreshToken) {
    public RefreshTokenRotation {
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(previousRefreshToken, "previousRefreshToken");
    }
}
