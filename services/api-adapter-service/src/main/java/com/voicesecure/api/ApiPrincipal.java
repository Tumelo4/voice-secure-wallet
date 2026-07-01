package com.voicesecure.api;

import java.util.Objects;

public record ApiPrincipal(String principalId) {
    public ApiPrincipal {
        principalId = Objects.requireNonNull(principalId, "principalId").trim();
        if (principalId.isEmpty()) {
            throw new IllegalArgumentException("principal id is required");
        }
    }
}
