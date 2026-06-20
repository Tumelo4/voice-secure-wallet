package com.voicesecure.launch;

public record SecurityScanResult(
        boolean owaspZapClean,
        int criticalCveCount,
        int highCveCount,
        boolean noSecretsDetected
) {
    public SecurityScanResult {
        if (criticalCveCount < 0) {
            throw new LaunchException("critical CVE count cannot be negative");
        }
        if (highCveCount < 0) {
            throw new LaunchException("high CVE count cannot be negative");
        }
    }
}
