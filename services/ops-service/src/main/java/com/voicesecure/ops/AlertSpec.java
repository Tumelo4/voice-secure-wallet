package com.voicesecure.ops;

import java.util.Objects;

public record AlertSpec(
        String name,
        AlertTier tier,
        String runbookLink
) {
    public AlertSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(runbookLink, "runbookLink");
        if (name.isBlank()) {
            throw new OpsException("alert name cannot be blank");
        }
        if (runbookLink.isBlank()) {
            throw new OpsException("alert runbook link cannot be blank");
        }
    }
}
