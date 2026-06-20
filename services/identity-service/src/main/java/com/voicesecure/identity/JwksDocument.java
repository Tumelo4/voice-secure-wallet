package com.voicesecure.identity;

import java.util.List;
import java.util.Objects;

public record JwksDocument(List<JsonWebKey> keys) {
    public JwksDocument {
        Objects.requireNonNull(keys, "keys");
        keys = List.copyOf(keys);
    }
}
