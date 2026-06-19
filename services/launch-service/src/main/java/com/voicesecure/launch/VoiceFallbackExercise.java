package com.voicesecure.launch;

public record VoiceFallbackExercise(
        int attemptedPayments,
        int successfulPayments,
        boolean voiceDegraded
) {
    public VoiceFallbackExercise {
        if (attemptedPayments < 0) {
            throw new LaunchException("attempted payments cannot be negative");
        }
        if (successfulPayments < 0) {
            throw new LaunchException("successful payments cannot be negative");
        }
        if (successfulPayments > attemptedPayments) {
            throw new LaunchException("successful payments cannot exceed attempted payments");
        }
    }
}
