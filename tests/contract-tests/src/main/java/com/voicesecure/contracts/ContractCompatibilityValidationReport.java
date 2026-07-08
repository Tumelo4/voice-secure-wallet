package com.voicesecure.contracts;

import java.util.List;
import java.util.Objects;

public record ContractCompatibilityValidationReport(boolean ready, List<String> blockers) {
    public ContractCompatibilityValidationReport {
        Objects.requireNonNull(blockers, "blockers");
        blockers = List.copyOf(blockers);
        if (ready != blockers.isEmpty()) {
            throw new IllegalArgumentException("ready must match blocker state");
        }
    }
}
