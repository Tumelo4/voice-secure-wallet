package com.voicesecure.fraud;

import java.time.Duration;

public record FraudPolicy(
        Duration velocityWindow,
        double approvalScoreThreshold,
        double baseScore,
        double amountRiskLimit,
        double amountRiskDivisor,
        int includedVelocityEvents,
        double velocityRiskStep,
        double velocityRiskLimit,
        double deviceRiskMultiplier,
        double deviceRiskLimit,
        int newDeviceAgeHours,
        double newDevicePenalty,
        double compliancePenalty,
        double devicePinScoreThreshold,
        double lowTrustDeviceThreshold,
        double voiceOtpScoreThreshold,
        double maxVoiceThreshold,
        double minVoiceThreshold,
        double voiceThresholdBase
) {
    public FraudPolicy {
        if (velocityWindow.isZero() || velocityWindow.isNegative()) {
            throw new FraudException("velocity window must be positive");
        }
        requireUnitInterval(approvalScoreThreshold, "approvalScoreThreshold");
        requireUnitInterval(baseScore, "baseScore");
        requireUnitInterval(amountRiskLimit, "amountRiskLimit");
        requirePositive(amountRiskDivisor, "amountRiskDivisor");
        requireUnitInterval(velocityRiskStep, "velocityRiskStep");
        requireUnitInterval(velocityRiskLimit, "velocityRiskLimit");
        requireUnitInterval(deviceRiskMultiplier, "deviceRiskMultiplier");
        requireUnitInterval(deviceRiskLimit, "deviceRiskLimit");
        if (includedVelocityEvents < 0) {
            throw new FraudException("included velocity events cannot be negative");
        }
        if (newDeviceAgeHours < 0) {
            throw new FraudException("new device age hours cannot be negative");
        }
        requireUnitInterval(newDevicePenalty, "newDevicePenalty");
        requireUnitInterval(compliancePenalty, "compliancePenalty");
        requireUnitInterval(devicePinScoreThreshold, "devicePinScoreThreshold");
        requireUnitInterval(lowTrustDeviceThreshold, "lowTrustDeviceThreshold");
        requireUnitInterval(voiceOtpScoreThreshold, "voiceOtpScoreThreshold");
        requireUnitInterval(maxVoiceThreshold, "maxVoiceThreshold");
        requireUnitInterval(minVoiceThreshold, "minVoiceThreshold");
        requireUnitInterval(voiceThresholdBase, "voiceThresholdBase");
        if (minVoiceThreshold > maxVoiceThreshold) {
            throw new FraudException("min voice threshold cannot exceed max voice threshold");
        }
    }

    public static FraudPolicy defaults() {
        return new FraudPolicy(
                Duration.ofMinutes(5),
                0.90,
                0.05,
                0.45,
                50000.0,
                3,
                0.05,
                0.25,
                0.40,
                0.35,
                24,
                0.10,
                0.50,
                0.65,
                0.40,
                0.25,
                0.95,
                0.55,
                0.90
        );
    }

    private static void requireUnitInterval(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new FraudException(name + " must be between 0.0 and 1.0");
        }
    }

    private static void requirePositive(double value, String name) {
        if (value <= 0.0) {
            throw new FraudException(name + " must be positive");
        }
    }
}
