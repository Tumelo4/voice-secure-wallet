package com.voicesecure.api;

import java.util.Objects;
import java.util.Set;

public record ProductionIngressPolicy(
        TlsVersion minimumTlsVersion,
        int maxRequestBodyKb,
        Set<String> publicHealthPaths
) {
    public ProductionIngressPolicy {
        Objects.requireNonNull(minimumTlsVersion, "minimumTlsVersion");
        Objects.requireNonNull(publicHealthPaths, "publicHealthPaths");
        publicHealthPaths = Set.copyOf(publicHealthPaths);
        if (maxRequestBodyKb <= 0) {
            throw new IllegalArgumentException("max request body KB must be positive");
        }
        if (publicHealthPaths.isEmpty()) {
            throw new IllegalArgumentException("public health paths cannot be empty");
        }
    }

    public static ProductionIngressPolicy defaults() {
        return new ProductionIngressPolicy(
                TlsVersion.TLS_1_3,
                256,
                Set.of("/health/live", "/health/ready")
        );
    }
}
