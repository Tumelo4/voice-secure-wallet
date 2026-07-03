package com.voicesecure.contracts;

import java.util.List;
import java.util.Objects;

public record ContractCompatibilityPlan(
        boolean pactBrokerReachableInCi,
        boolean schemaRegistryReachableInCi,
        boolean ciBlocksOnVerificationFailure,
        List<ContractArtifact> artifacts
) {
    public ContractCompatibilityPlan {
        Objects.requireNonNull(artifacts, "artifacts");
        artifacts = List.copyOf(artifacts);
    }
}
