package com.nexswitch.qa.domain.model;

import java.util.List;
import java.util.Map;

public record TestRun(
    String id,
    String name,
    List<RunScenarioRef> scenarios,   // ordered; variables captured in one scenario flow to the next
    Map<String, Object> runVariables, // baseline variables available to all scenarios in this run
    SessionConfig session             // controls variable sharing across scenarios
) {
    // LEARN: STATEFUL carries captured variables across scenario boundaries (e.g. login token
    //        from scenario 1 flows into scenario 2). STATELESS resets context each scenario.
    public enum SessionMode { STATEFUL, STATELESS }

    public record SessionConfig(
        SessionMode mode,
        List<String> carryVariables,  // empty = carry all captured variables; non-empty = allowlist
        boolean isolateOnFailure      // if true, stop carrying variables after a scenario failure
    ) {
        public SessionConfig {
            carryVariables = carryVariables == null ? List.of() : List.copyOf(carryVariables);
        }

        public static SessionConfig stateless() {
            return new SessionConfig(SessionMode.STATELESS, List.of(), false);
        }

        public static SessionConfig stateful() {
            return new SessionConfig(SessionMode.STATEFUL, List.of(), true);
        }
    }

    public record RunScenarioRef(
        String scenarioId,
        Map<String, Object> variableOverrides  // override run-level defaults for this scenario only
    ) {
        public RunScenarioRef {
            variableOverrides = variableOverrides == null ? Map.of() : Map.copyOf(variableOverrides);
        }
    }

    public TestRun {
        if (id == null || id.isBlank())               throw new IllegalArgumentException("run id must not be blank");
        if (scenarios == null || scenarios.isEmpty()) throw new IllegalArgumentException("run must reference at least one scenario");
        runVariables = runVariables == null ? Map.of() : Map.copyOf(runVariables);
        scenarios    = List.copyOf(scenarios);
        session      = session == null ? SessionConfig.stateful() : session;
    }
}
