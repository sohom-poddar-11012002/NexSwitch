package com.nexswitch.qa.domain.model;

import java.util.List;
import java.util.Map;

public record TestRun(
    String id,
    String name,
    List<RunScenarioRef> scenarios,   // ordered; variables captured in one scenario flow to the next
    Map<String, Object> runVariables  // baseline variables available to all scenarios in this run
) {
    public record RunScenarioRef(
        String scenarioId,
        Map<String, Object> variableOverrides  // override run-level defaults for this scenario only
    ) {
        public RunScenarioRef {
            variableOverrides = variableOverrides == null ? Map.of() : Map.copyOf(variableOverrides);
        }
    }

    public TestRun {
        if (id == null || id.isBlank())            throw new IllegalArgumentException("run id must not be blank");
        if (scenarios == null || scenarios.isEmpty()) throw new IllegalArgumentException("run must reference at least one scenario");
        runVariables = runVariables == null ? Map.of() : Map.copyOf(runVariables);
        scenarios    = List.copyOf(scenarios);
    }
}
