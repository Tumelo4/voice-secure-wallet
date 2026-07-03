package com.voicesecure.contracts;

import java.util.Objects;
import java.util.Set;

public record ContractCompatibilityPolicy(
        Set<String> requiredEventTypes,
        SchemaCompatibilityMode requiredCompatibilityMode
) {
    public ContractCompatibilityPolicy {
        Objects.requireNonNull(requiredEventTypes, "requiredEventTypes");
        Objects.requireNonNull(requiredCompatibilityMode, "requiredCompatibilityMode");
        requiredEventTypes = Set.copyOf(requiredEventTypes);
        if (requiredEventTypes.isEmpty()) {
            throw new IllegalArgumentException("required event types cannot be empty");
        }
    }

    public static ContractCompatibilityPolicy defaults() {
        return new ContractCompatibilityPolicy(
                Set.of("fraud.scored", "compliance.hit"),
                SchemaCompatibilityMode.BACKWARD_TRANSITIVE
        );
    }
}
