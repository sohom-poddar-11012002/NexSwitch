package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.model.ResumeOutcome;
import com.nexswitch.qa.domain.port.inbound.ResumeStepUseCase;
import com.nexswitch.qa.domain.service.ScenarioExecutionEngine;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ResumeStepService implements ResumeStepUseCase {

    private final ScenarioExecutionEngine engine;

    public ResumeStepService(ScenarioExecutionEngine engine) {
        this.engine = engine;
    }

    @Override
    public void resume(UUID executionId, String stepId, ResumeOutcome outcome) {
        engine.resumeWait(executionId, stepId, outcome);
    }
}
