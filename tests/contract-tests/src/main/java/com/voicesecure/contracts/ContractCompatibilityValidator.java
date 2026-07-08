package com.voicesecure.contracts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ContractCompatibilityValidator {
    private final ContractCompatibilityPolicy policy;

    public ContractCompatibilityValidator() {
        this(ContractCompatibilityPolicy.defaults());
    }

    public ContractCompatibilityValidator(ContractCompatibilityPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public ContractCompatibilityValidationReport validate(ContractCompatibilityPlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<String> blockers = new ArrayList<>();
        validateCiControls(plan, blockers);
        validateArtifacts(plan, blockers);
        return new ContractCompatibilityValidationReport(blockers.isEmpty(), blockers);
    }

    private void validateCiControls(ContractCompatibilityPlan plan, List<String> blockers) {
        if (!plan.pactBrokerReachableInCi()) {
            blockers.add("Pact broker must be reachable in CI");
        }
        if (!plan.schemaRegistryReachableInCi()) {
            blockers.add("Schema Registry must be reachable in CI");
        }
        if (!plan.ciBlocksOnVerificationFailure()) {
            blockers.add("CI must fail when Pact verification fails");
        }
    }

    private void validateArtifacts(ContractCompatibilityPlan plan, List<String> blockers) {
        Map<String, ContractArtifact> artifactsByType = plan.artifacts().stream()
                .collect(Collectors.toMap(ContractArtifact::eventType, Function.identity(), (first, second) -> first));

        for (String requiredEventType : policy.requiredEventTypes()) {
            ContractArtifact artifact = artifactsByType.get(requiredEventType);
            if (artifact == null) {
                blockers.add(requiredEventType + " contract artifact is required");
                continue;
            }
            validateArtifact(artifact, blockers);
        }
    }

    private void validateArtifact(ContractArtifact artifact, List<String> blockers) {
        String eventType = artifact.eventType();
        if (artifact.schemaSubject().isBlank()) {
            blockers.add(eventType + " schema subject is required");
        }
        if (artifact.consumers().isEmpty()) {
            blockers.add(eventType + " must list at least one consumer");
        }
        if (!artifact.pactPublished()) {
            blockers.add(eventType + " contract must be published to Pact broker");
        }
        if (!artifact.consumersVerified()) {
            blockers.add(eventType + " consumers must verify the latest Pact");
        }
        if (!artifact.schemaRegistered()) {
            blockers.add(eventType + " schema must be registered");
        }
        if (!artifact.schemaIdPinned()) {
            blockers.add(eventType + " schema id must be pinned in release evidence");
        }
        if (artifact.compatibilityMode() != policy.requiredCompatibilityMode()) {
            blockers.add(eventType + " must use " + policy.requiredCompatibilityMode() + " schema compatibility");
        }
    }
}
