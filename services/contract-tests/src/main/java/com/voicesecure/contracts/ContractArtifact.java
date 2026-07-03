package com.voicesecure.contracts;

import java.util.Objects;
import java.util.Set;

public record ContractArtifact(
        String eventType,
        String schemaSubject,
        Set<String> consumers,
        boolean pactPublished,
        boolean consumersVerified,
        boolean schemaRegistered,
        boolean schemaIdPinned,
        SchemaCompatibilityMode compatibilityMode
) {
    public ContractArtifact {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(schemaSubject, "schemaSubject");
        Objects.requireNonNull(consumers, "consumers");
        Objects.requireNonNull(compatibilityMode, "compatibilityMode");
        consumers = Set.copyOf(consumers);
    }
}
