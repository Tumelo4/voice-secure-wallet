package com.voicesecure.launch;

public record PerformanceTestResult(
        int loadMultiplier,
        boolean p99LatencyWithinSlo
) {
    public PerformanceTestResult {
        if (loadMultiplier <= 0) {
            throw new LaunchException("load multiplier must be positive");
        }
    }
}
