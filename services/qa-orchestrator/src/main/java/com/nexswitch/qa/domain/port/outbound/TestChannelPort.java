package com.nexswitch.qa.domain.port.outbound;

import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;

import java.util.Map;

// LEARN: TestChannelPort — single interface all channel adapters implement. ScenarioExecutionEngine
//        iterates List<TestChannelPort> and calls supports() to dispatch — adding a new channel
//        (SOAP, gRPC, NFC) means adding one class, not modifying the engine.
public interface TestChannelPort {

    boolean supports(ChannelType channelType);

    StepResult.Passed execute(TestStep.Send step, Map<String, Object> context) throws Exception;
}
