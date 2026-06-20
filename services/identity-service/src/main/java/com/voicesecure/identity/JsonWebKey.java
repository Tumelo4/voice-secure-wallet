package com.voicesecure.identity;

import java.util.Objects;

public record JsonWebKey(String keyId, String keyType, String algorithm, String use, String modulus, String exponent) {
    public JsonWebKey {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(keyType, "keyType");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(use, "use");
        Objects.requireNonNull(modulus, "modulus");
        Objects.requireNonNull(exponent, "exponent");
    }
}
