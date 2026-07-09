package com.voicesecure.ops;

import java.util.List;
import java.util.Objects;

public record ServiceTelemetrySpec(
        String serviceName,
        List<String> goldenSignals,
        List<String> structuredLogFields,
        String traceHeader,
        boolean otelEnabled
) {
    public ServiceTelemetrySpec {
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(goldenSignals, "goldenSignals");
        Objects.requireNonNull(structuredLogFields, "structuredLogFields");
        Objects.requireNonNull(traceHeader, "traceHeader");
        goldenSignals = List.copyOf(goldenSignals);
        structuredLogFields = List.copyOf(structuredLogFields);
        if (serviceName.isBlank()) {
            throw new OpsException("service name cannot be blank");
        }
        if (traceHeader.isBlank()) {
            throw new OpsException("trace header cannot be blank");
        }
    }
}
