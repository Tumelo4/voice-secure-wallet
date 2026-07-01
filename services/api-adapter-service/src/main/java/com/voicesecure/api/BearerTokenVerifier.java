package com.voicesecure.api;

import java.util.Optional;

@FunctionalInterface
public interface BearerTokenVerifier {
    Optional<ApiPrincipal> verify(String token);
}
