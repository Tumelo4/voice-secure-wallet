package com.voicesecure.api;

import com.voicesecure.identity.AccessTokenClaims;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record ApiPrincipal(String principalId, Set<String> scopes) {
    public ApiPrincipal {
        principalId = Objects.requireNonNull(principalId, "principalId").trim();
        if (principalId.isEmpty()) {
            throw new IllegalArgumentException("principal id is required");
        }
        scopes = normalizeScopes(scopes);
    }

    @Override
    public Set<String> scopes() {
        return new LinkedHashSet<>(scopes);
    }

    public static ApiPrincipal of(String principalId, String... scopes) {
        LinkedHashSet<String> normalizedScopes = new LinkedHashSet<>();
        if (scopes != null) {
            for (String scope : scopes) {
                addScope(normalizedScopes, scope);
            }
        }
        return new ApiPrincipal(principalId, normalizedScopes);
    }

    public static ApiPrincipal fromScopeString(String principalId, String scopeString) {
        return new ApiPrincipal(principalId, parseScopes(scopeString));
    }

    public static ApiPrincipal fromClaims(AccessTokenClaims claims) {
        Objects.requireNonNull(claims, "claims");
        return fromScopeString(claims.subjectUserId().toString(), claims.scope());
    }

    public boolean hasScope(String requiredScope) {
        if (requiredScope == null || requiredScope.isBlank()) {
            return false;
        }
        return hasAnyScope(Set.of(requiredScope));
    }

    public boolean hasAnyScope(Collection<String> requiredScopes) {
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return true;
        }
        for (String requiredScope : requiredScopes) {
            String normalizedScope = normalizeScope(requiredScope);
            if (!normalizedScope.isEmpty() && scopes.contains(normalizedScope)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizeScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalizedScopes = new LinkedHashSet<>();
        for (String scope : scopes) {
            addScope(normalizedScopes, scope);
        }
        return Set.copyOf(normalizedScopes);
    }

    private static Set<String> parseScopes(String scopeString) {
        LinkedHashSet<String> parsedScopes = new LinkedHashSet<>();
        if (scopeString == null || scopeString.isBlank()) {
            return parsedScopes;
        }
        for (String scope : scopeString.split("\\s+")) {
            addScope(parsedScopes, scope);
        }
        return parsedScopes;
    }

    private static void addScope(Collection<String> scopes, String scope) {
        String normalizedScope = normalizeScope(scope);
        if (!normalizedScope.isEmpty()) {
            scopes.add(normalizedScope);
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null) {
            return "";
        }
        String normalizedScope = scope.trim().toLowerCase(Locale.ROOT);
        return normalizedScope.isEmpty() ? "" : normalizedScope;
    }
}
