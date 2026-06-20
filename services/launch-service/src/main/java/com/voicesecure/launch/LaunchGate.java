package com.voicesecure.launch;

import java.util.List;

interface LaunchGate {
    void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers);
}
