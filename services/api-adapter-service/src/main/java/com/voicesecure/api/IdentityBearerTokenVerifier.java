package com.voicesecure.api;

import com.voicesecure.identity.IdentityException;
import com.voicesecure.identity.IdentityService;
import java.util.Objects;
import java.util.Optional;

public final class IdentityBearerTokenVerifier implements BearerTokenVerifier {
    private final IdentityService identityService;

    public IdentityBearerTokenVerifier(IdentityService identityService) {
        this.identityService = Objects.requireNonNull(identityService, "identityService");
    }

    @Override
    public Optional<ApiPrincipal> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ApiPrincipal.fromClaims(identityService.verifyAccessToken(token.trim())));
        } catch (IdentityException ex) {
            return Optional.empty();
        }
    }
}
