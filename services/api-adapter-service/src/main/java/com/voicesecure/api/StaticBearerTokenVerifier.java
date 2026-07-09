package com.voicesecure.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StaticBearerTokenVerifier implements BearerTokenVerifier {
    private final Map<String, ApiPrincipal> tokenPrincipals;

    private StaticBearerTokenVerifier(Map<String, ApiPrincipal> tokenPrincipals) {
        this.tokenPrincipals = Map.copyOf(Objects.requireNonNull(tokenPrincipals, "tokenPrincipals"));
    }

    public static StaticBearerTokenVerifier of(Map<String, ApiPrincipal> tokenPrincipals) {
        return new StaticBearerTokenVerifier(tokenPrincipals);
    }

    @Override
    public Optional<ApiPrincipal> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        ApiPrincipal principal = tokenPrincipals.get(token.trim());
        return principal == null ? Optional.empty() : Optional.of(principal);
    }
}
