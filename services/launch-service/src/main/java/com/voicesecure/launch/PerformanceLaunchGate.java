package com.voicesecure.launch;

import java.util.List;

final class PerformanceLaunchGate implements LaunchGate {
    @Override
    public void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers) {
        PerformanceTestResult performanceTestResult = plan.performanceTestResult();
        if (performanceTestResult.loadMultiplier() < policy.requiredLoadMultiplier()) {
            blockers.add("performance test must reach " + policy.requiredLoadMultiplier() + "x load");
        }
        if (!performanceTestResult.p99LatencyWithinSlo()) {
            blockers.add("p99 latency must stay within SLO under load");
        }
    }
}
