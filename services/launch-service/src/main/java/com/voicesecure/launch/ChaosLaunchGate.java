package com.voicesecure.launch;

import java.util.List;
import java.util.Set;

final class ChaosLaunchGate implements LaunchGate {
    @Override
    public void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers) {
        if (!Set.copyOf(plan.chaosTestSuite().scenarios()).equals(policy.requiredChaosScenarios())) {
            blockers.add(policy.requiredChaosScenarios().size() == 5
                    ? "all five chaos scenarios must be exercised"
                    : "all required chaos scenarios must be exercised");
        }
        if (!plan.chaosTestSuite().voiceFallbackCompletesPayment()) {
            blockers.add("voice outage chaos test must prove OTP fallback completes payment");
        }
    }
}
