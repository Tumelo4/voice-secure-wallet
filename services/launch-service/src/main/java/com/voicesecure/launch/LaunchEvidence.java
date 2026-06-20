package com.voicesecure.launch;

import java.util.Objects;

public record LaunchEvidence(
        String testRunId,
        int measuredP99LatencyMs,
        double loadMultiplier,
        int falsePositiveSampleSize,
        int rtoMinutes,
        int rpoMinutes,
        String cveScanSource,
        String penTestReportReference
) {
    public LaunchEvidence {
        Objects.requireNonNull(testRunId, "testRunId");
        Objects.requireNonNull(cveScanSource, "cveScanSource");
        Objects.requireNonNull(penTestReportReference, "penTestReportReference");
    }
}
