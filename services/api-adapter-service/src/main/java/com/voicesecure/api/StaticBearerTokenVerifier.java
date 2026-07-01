package com.voicesecure.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StaticBearerTokenVerifier implements BearerTokenVerifier {
    private final Map<String, String> tokenPrincipals;

    private StaticBearerTokenVerifier(Map<String, String> tokenPrincipals) {
        this.tokenPrincipals = Map.copyOf(Objects.requireNonNull(tokenPrincipals, "tokenPrincipals"));
    }

    public static StaticBearerTokenVerifier of(Map<String, String> tokenPrincipals) {
        return new StaticBearerTokenVerifier(tokenPrincipals);
    }

    @Override
    public Optional<ApiPrincipal> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String principalId = tokenPrincipals.get(token.trim());
        return principalId == null ? Optional.empty() : Optional.of(new ApiPrincipal(principalId));
    }
}
