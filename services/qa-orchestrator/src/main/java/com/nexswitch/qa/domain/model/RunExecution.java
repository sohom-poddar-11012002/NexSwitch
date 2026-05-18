package com.nexswitch.qa.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// LEARN: Immutable run snapshot — each state change (RUNNING→PASSED) creates a new RunExecution
//        record rather than mutating in place. The repository always overwrites with the latest.
public record RunExecution(
    UUID id,
    String runId,
    ExecutionStatus status,
    List<ScenarioExecution> scenarioExecutions,
    Map<String, Object> sharedContext,    // variables accumulated across all scenarios in this run
    Instant startedAt,
    Instant completedAt
) {
    public record ScenarioExecution(
        String scenarioId,
        int orderIndex,
        List<StepExecution> stepExecutions,
        ExecutionStatus status
    ) {}

    public record StepExecution(
        String stepId,
        int stepIndex,
        StepResult result,
        Instant executedAt
    ) {}
}
