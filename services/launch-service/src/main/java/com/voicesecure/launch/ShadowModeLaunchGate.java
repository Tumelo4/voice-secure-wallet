package com.voicesecure.launch;

import java.util.List;

final class ShadowModeLaunchGate implements LaunchGate {
    @Override
    public void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers) {
        VoiceShadowModeValidation shadowModeValidation = plan.voiceShadowModeValidation();
        if (shadowModeValidation.hours() < policy.requiredShadowModeHours()) {
            blockers.add("voice shadow mode must run for " + policy.requiredShadowModeHours() + " hours");
        }
        if (shadowModeValidation.falsePositiveRate() >= policy.maxFalsePositiveRate()) {
            blockers.add("voice shadow mode false positive rate must stay below 0.1%");
        }
    }
}
