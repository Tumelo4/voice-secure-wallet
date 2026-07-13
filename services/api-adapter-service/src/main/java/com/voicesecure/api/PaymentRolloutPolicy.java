package com.voicesecure.api;

public record PaymentRolloutPolicy(boolean customerIntentEnabled) {
    public static PaymentRolloutPolicy enabled() {
        return new PaymentRolloutPolicy(true);
    }

    public static PaymentRolloutPolicy disabled() {
        return new PaymentRolloutPolicy(false);
    }
}
