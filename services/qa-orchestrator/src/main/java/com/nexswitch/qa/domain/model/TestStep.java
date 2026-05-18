package com.nexswitch.qa.domain.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// LEARN: Sealed interface + records — compiler enforces exhaustive switch in ScenarioExecutionEngine.
//        Every new step type forces a compile error at every dispatch point, preventing silent misses.
public sealed interface TestStep
        permits TestStep.Send,
                TestStep.Assert,
                TestStep.WaitForHuman,
                TestStep.Loop,
                TestStep.InjectVariable {

    // Sends a message through a channel adapter; response captured into context as captureResponseAs
    record Send(
        ChannelType channel,
        String operation,            // "auth_request_0100", "GET /api/transactions/by-stan/{{stan}}", etc.
        Map<String, Object> payload,
        Duration timeout,
        String captureResponseAs     // variable name stored in run context for downstream assertions
    ) implements TestStep {}

    // Evaluates a SpEL or JSONPath expression against the execution context; fails fast if failFast=true
    record Assert(
        String expression,           // SpEL: "auth_response.field39 == '00'" or JSONPath: "$.status == 'AUTHORIZED'"
        String description,
        boolean failFast
    ) implements TestStep {}

    // LEARN: Human-in-the-loop — parks a virtual thread on a CompletableFuture until the QA engineer
    //        clicks Continue/Fail in the portal or the timeout fires. Cost: near-zero (virtual thread).
    record WaitForHuman(
        String stepId,               // unique within the scenario; used in resume endpoint URL
        String instruction,          // shown as banner in portal: "Now swipe card on TERM0042"
        Duration timeout             // auto-fail if not resumed within this window
    ) implements TestStep {}

    // Repeats inner steps N times; when parallel=true all iterations fire concurrently on virtual threads
    record Loop(
        int count,
        Duration delayBetween,
        boolean parallel,
        List<TestStep> steps
    ) implements TestStep {}

    // Evaluates an expression and stores result under name in execution context
    record InjectVariable(
        String name,
        String expression            // "{{$stan}}", "{{$uuid}}", literal value, or SpEL expression
    ) implements TestStep {}
}
