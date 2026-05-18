package com.nexswitch.qa.domain.port.inbound;

import com.nexswitch.qa.domain.model.RunExecution;

import java.util.Map;
import java.util.UUID;

// LEARN: Command pattern as inner record — bundles all trigger inputs into a single immutable value
//        object. The interface and its command live together; callers can't construct a partial command.
public interface TriggerRunUseCase {

    record TriggerRunCommand(
        String runId,
        Map<String, Object> variableOverrides  // runtime overrides applied on top of run-level defaults
    ) {}

    UUID trigger(TriggerRunCommand command);  // returns executionId; run starts async on virtual thread
}
