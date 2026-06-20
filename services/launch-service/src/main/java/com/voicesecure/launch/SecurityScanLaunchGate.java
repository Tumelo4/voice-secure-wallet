package com.voicesecure.launch;

import java.util.List;

final class SecurityScanLaunchGate implements LaunchGate {
    @Override
    public void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers) {
        SecurityScanResult securityScanResult = plan.securityScanResult();
        if (!securityScanResult.owaspZapClean()) {
            blockers.add("OWASP ZAP must be clean in CI");
        }
        if (securityScanResult.criticalCveCount() != 0 || securityScanResult.highCveCount() != 0) {
            blockers.add("no Critical/High CVEs may remain in container images");
        }
        if (!securityScanResult.noSecretsDetected()) {
            blockers.add("no secrets may be detected in code, env, or images");
        }
    }
}
