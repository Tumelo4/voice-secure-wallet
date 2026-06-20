package com.voicesecure.launch;

import java.util.List;

final class VoiceFallbackLaunchGate implements LaunchGate {
    @Override
    public void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers) {
        VoiceFallbackExercise voiceFallbackExercise = plan.voiceFallbackExercise();
        if (!voiceFallbackExercise.voiceDegraded()) {
            blockers.add("voice fallback exercise must intentionally degrade voice");
        }
        if (voiceFallbackExercise.attemptedPayments() != policy.requiredFallbackAttempts()
                || voiceFallbackExercise.successfulPayments() != policy.requiredFallbackSuccesses()) {
            blockers.add("voice OTP fallback must succeed for "
                    + policy.requiredFallbackSuccesses()
                    + " of "
                    + policy.requiredFallbackAttempts()
                    + " test payments");
        }
    }
}
