package com.voicesecure.launch;

import java.util.Objects;
import java.util.Set;

public record LaunchReadinessPolicy(
        Set<ChaosScenario> requiredChaosScenarios,
        int requiredShadowModeHours,
        double maxFalsePositiveRate,
        int requiredLoadMultiplier,
        int requiredFallbackAttempts,
        int requiredFallbackSuccesses,
        int maximumRollbackMinutes
) {
    public LaunchReadinessPolicy {
        Objects.requireNonNull(requiredChaosScenarios, "requiredChaosScenarios");
        requiredChaosScenarios = Set.copyOf(requiredChaosScenarios);
        if (requiredChaosScenarios.isEmpty()) {
            throw new LaunchException("required chaos scenarios cannot be empty");
        }
        if (requiredShadowModeHours <= 0) {
            throw new LaunchException("required shadow mode hours must be positive");
        }
        if (maxFalsePositiveRate <= 0.0 || maxFalsePositiveRate >= 1.0) {
            throw new LaunchException("max false positive rate must be between 0.0 and 1.0");
        }
        if (requiredLoadMultiplier <= 0) {
            throw new LaunchException("required load multiplier must be positive");
        }
        if (requiredFallbackAttempts <= 0) {
            throw new LaunchException("required fallback attempts must be positive");
        }
        if (requiredFallbackSuccesses <= 0 || requiredFallbackSuccesses > requiredFallbackAttempts) {
            throw new LaunchException("required fallback successes must be positive and cannot exceed attempts");
        }
        if (maximumRollbackMinutes <= 0) {
            throw new LaunchException("maximum rollback minutes must be positive");
        }
    }

    public static LaunchReadinessPolicy defaults() {
        return new LaunchReadinessPolicy(
                Set.of(
                        ChaosScenario.AWS_FIS_DEPENDENCY_FAULT,
                        ChaosScenario.AWS_FIS_LATENCY_SPIKE,
                        ChaosScenario.TOXIPROXY_VOICE_OUTAGE,
                        ChaosScenario.TOXIPROXY_KAFKA_PARTITION,
                        ChaosScenario.REGION_FAILOVER
                ),
                48,
                0.001,
                10,
                100,
                100,
                30
        );
    }
}
