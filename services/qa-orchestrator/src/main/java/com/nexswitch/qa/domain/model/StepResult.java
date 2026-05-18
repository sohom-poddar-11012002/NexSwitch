package com.nexswitch.qa.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

// LEARN: Sealed result type — the SSE publisher and persistence mapper switch on this
//        exhaustively. Adding a new outcome requires handling it in every downstream site.
public sealed interface StepResult
        permits StepResult.Passed,
                StepResult.Failed,
                StepResult.Skipped,
                StepResult.WaitingForHuman,
                StepResult.TimedOut {

    record Passed(
        String stepId,
        Duration elapsed,
        Map<String, Object> captured  // variables captured from response, merged into run context
    ) implements StepResult {}

    record Failed(
        String stepId,
        String reason,
        String actualValue,
        String expectedValue,
        Duration elapsed
    ) implements StepResult {}

    record Skipped(
        String stepId,
        String reason  // "prior step failed with fail_fast=true"
    ) implements StepResult {}

    record WaitingForHuman(
        String stepId,
        String instruction,
        Instant expiresAt
    ) implements StepResult {}

    record TimedOut(
        String stepId,
        Duration elapsed
    ) implements StepResult {}

    default String stepId() {
        return switch (this) {
            case Passed p           -> p.stepId();
            case Failed f           -> f.stepId();
            case Skipped s          -> s.stepId();
            case WaitingForHuman w  -> w.stepId();
            case TimedOut t         -> t.stepId();
        };
    }
}
