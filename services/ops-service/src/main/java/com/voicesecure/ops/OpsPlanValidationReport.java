package com.voicesecure.ops;

import java.util.List;
import java.util.Objects;

public record OpsPlanValidationReport(
        boolean ready,
        List<String> blockers
) {
    public OpsPlanValidationReport {
        Objects.requireNonNull(blockers, "blockers");
        blockers = List.copyOf(blockers);
    }
}
