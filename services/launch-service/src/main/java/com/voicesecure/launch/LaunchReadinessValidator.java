package com.voicesecure.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LaunchReadinessValidator {
    private final LaunchReadinessPolicy policy;
    private final List<LaunchGate> gates;

    public LaunchReadinessValidator() {
        this(LaunchReadinessPolicy.defaults());
    }

    public LaunchReadinessValidator(LaunchReadinessPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.gates = List.of(
                new CoreLaunchGate(),
                new ChaosLaunchGate(),
                new PenTestLaunchGate(),
                new SecurityScanLaunchGate(),
                new ShadowModeLaunchGate(),
                new PerformanceLaunchGate(),
                new VoiceFallbackLaunchGate(),
                new ProductionCutoverLaunchGate(),
                new BenchmarkEvidenceLaunchGate()
        );
    }

    public LaunchReadinessReport validate(LaunchReadinessPlan plan) {
        List<String> blockers = new ArrayList<>();
        for (LaunchGate gate : gates) {
            gate.validate(plan, policy, blockers);
        }
        return new LaunchReadinessReport(blockers.isEmpty(), blockers);
    }
}
