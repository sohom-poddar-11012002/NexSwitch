package com.nexswitch.qa.domain.port.inbound;

import com.nexswitch.qa.domain.model.RunExecution;

import java.util.Map;
import java.util.UUID;

public interface TriggerRunUseCase {

    record TriggerRunCommand(
        String runId,
        Map<String, Object> variableOverrides  // runtime overrides applied on top of run-level defaults
    ) {}

    UUID trigger(TriggerRunCommand command);  // returns executionId; run starts async on virtual thread
}
