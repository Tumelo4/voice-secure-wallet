package com.voicesecure.launch;

public record VoiceShadowModeValidation(
        int hours,
        double falsePositiveRate
) {
    public VoiceShadowModeValidation {
        if (hours < 0) {
            throw new LaunchException("shadow mode hours cannot be negative");
        }
        if (falsePositiveRate < 0.0) {
            throw new LaunchException("false positive rate cannot be negative");
        }
    }
}
