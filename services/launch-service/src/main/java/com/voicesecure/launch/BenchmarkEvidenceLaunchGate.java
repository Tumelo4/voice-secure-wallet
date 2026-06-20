package com.voicesecure.launch;

import java.util.List;

final class BenchmarkEvidenceLaunchGate implements LaunchGate {
    @Override
    public void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers) {
        LaunchEvidence evidence = plan.launchEvidence();
        if (evidence.testRunId().isBlank()) {
            blockers.add("benchmark evidence must include a test run id");
        }
        if (evidence.measuredP99LatencyMs() <= 0) {
            blockers.add("benchmark evidence must include measured p99 latency");
        }
        if (evidence.loadMultiplier() < policy.requiredLoadMultiplier()) {
            blockers.add("benchmark evidence load multiplier must meet launch policy");
        }
        if (evidence.falsePositiveSampleSize() <= 0) {
            blockers.add("benchmark evidence must include voice sample size");
        }
        if (evidence.rtoMinutes() <= 0 || evidence.rpoMinutes() <= 0) {
            blockers.add("benchmark evidence must include RTO/RPO minutes");
        }
        if (evidence.cveScanSource().isBlank()) {
            blockers.add("benchmark evidence must include CVE scan source");
        }
        if (evidence.penTestReportReference().isBlank()) {
            blockers.add("benchmark evidence must include pen-test report reference");
        }
    }
}
