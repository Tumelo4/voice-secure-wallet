package com.voicesecure.payments;

public enum AuthPolicy {
    VOICE_ONLY(null),
    VOICE_OTP(FallbackMethod.OTP),
    DEVICE_PIN(FallbackMethod.PIN);

    private final FallbackMethod fallbackMethod;

    AuthPolicy(FallbackMethod fallbackMethod) {
        this.fallbackMethod = fallbackMethod;
    }

    public boolean permitsFallback() {
        return fallbackMethod != null;
    }

    public FallbackMethod fallbackMethod() {
        if (fallbackMethod == null) {
            throw new PaymentException("auth policy does not permit fallback");
        }
        return fallbackMethod;
    }
}
