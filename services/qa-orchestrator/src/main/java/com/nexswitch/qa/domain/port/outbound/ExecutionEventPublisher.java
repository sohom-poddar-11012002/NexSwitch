package com.nexswitch.qa.domain.port.outbound;

import com.nexswitch.qa.domain.model.ExecutionStatus;
import com.nexswitch.qa.domain.model.StepResult;

import java.time.Instant;
import java.util.UUID;

// Decouples the domain execution engine from SSE transport.
// The SseEventPublisher adapter implements this.
public interface ExecutionEventPublisher {

    void publishRunStarted(UUID executionId, String runId);

    void publishScenarioStarted(UUID executionId, String scenarioId, int orderIndex);

    void publishStepStarted(UUID executionId, String stepId, String description);

    void publishStepResult(UUID executionId, String stepId, StepResult result);

    void publishWaitForHuman(UUID executionId, String stepId, String instruction, Instant expiresAt);

    void publishScenarioComplete(UUID executionId, String scenarioId, ExecutionStatus status);

    void publishRunComplete(UUID executionId, ExecutionStatus finalStatus);

    void publishSuiteStarted(UUID suiteExecutionId, String suiteId);

    void publishSuiteComplete(UUID suiteExecutionId, ExecutionStatus status, int passed, int failed);
}
