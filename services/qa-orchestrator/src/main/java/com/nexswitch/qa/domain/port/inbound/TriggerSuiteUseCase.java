package com.nexswitch.qa.domain.port.inbound;

import java.util.Map;
import java.util.UUID;

public interface TriggerSuiteUseCase {

    UUID trigger(TriggerSuiteCommand command);

    record TriggerSuiteCommand(String suiteId, Map<String, Object> variableOverrides) {}
}
