package com.nexswitch.qa.domain.port.inbound;

import com.nexswitch.qa.domain.model.ResumeOutcome;

import java.util.UUID;

public interface ResumeStepUseCase {

    void resume(UUID executionId, String stepId, ResumeOutcome outcome);
}
