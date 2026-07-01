package com.voicesecure.notifications;

import java.util.Objects;

public final class DeterministicOtpGenerator implements OtpGenerator {
    private final String code;

    public DeterministicOtpGenerator(String code) {
        this.code = Objects.requireNonNull(code, "code");
        if (!code.matches("[0-9]{6}")) {
            throw new NotificationException("deterministic OTP code must be 6 digits");
        }
    }

    @Override
    public String generate() {
        return code;
    }
}
