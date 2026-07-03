package com.voicesecure.api;

import java.util.List;
import java.util.Objects;

public record ProductionIngressValidationReport(boolean ready, List<String> blockers) {
    public ProductionIngressValidationReport {
        Objects.requireNonNull(blockers, "blockers");
        blockers = List.copyOf(blockers);
        if (ready != blockers.isEmpty()) {
            throw new IllegalArgumentException("ready must match blocker state");
        }
    }
}
