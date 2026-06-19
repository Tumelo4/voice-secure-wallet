package com.voicesecure.launch;

import java.util.List;
import java.util.Objects;

public record ChaosTestSuite(
        List<ChaosScenario> scenarios,
        boolean voiceFallbackCompletesPayment
) {
    public ChaosTestSuite {
        Objects.requireNonNull(scenarios, "scenarios");
        scenarios = List.copyOf(scenarios);
    }
}
