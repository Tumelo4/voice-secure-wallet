package com.voicesecure.api;

import java.util.Objects;
import java.util.Set;

public record ProductionIngressPlan(
        boolean tlsTerminatedAtEdge,
        TlsVersion minimumTlsVersion,
        boolean mutualTlsRequired,
        boolean clientCertificateForwarded,
        boolean oidcJwksConfigured,
        boolean distributedRateLimitStore,
        boolean wafEnabled,
        boolean hstsEnabled,
        boolean traceHeaderForwarding,
        boolean requestBodyLimitEnabled,
        int maxRequestBodyKb,
        Set<String> publicPaths
) {
    public ProductionIngressPlan {
        Objects.requireNonNull(minimumTlsVersion, "minimumTlsVersion");
        Objects.requireNonNull(publicPaths, "publicPaths");
        publicPaths = Set.copyOf(publicPaths);
    }
}
