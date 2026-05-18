package com.nexswitch.qa.domain.service;

import com.nexswitch.qa.domain.model.*;
import com.nexswitch.qa.domain.port.outbound.ExecutionEventPublisher;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

// LEARN: Virtual threads (JDK 21+) — each WaitForHuman step parks cheaply on a CompletableFuture;
//        no OS thread is blocked. A full suite with 50 parallel runs + human pauses costs almost
//        no thread pool resource. JDK 26 makes virtual threads the default for structured tasks.
public class ScenarioExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ScenarioExecutionEngine.class);

    // Keyed by "executionId:stepId" — parks a virtual thread until resume() or timeout
    private final Map<String, CompletableFuture<ResumeOutcome>> pendingResumes = new ConcurrentHashMap<>();

    private final List<TestChannelPort> channels;
    private final ExecutionEventPublisher eventPublisher;
    private final VariableResolver variableResolver;
    private final AssertionEvaluator assertionEvaluator;

    public ScenarioExecutionEngine(
            List<TestChannelPort> channels,
            ExecutionEventPublisher eventPublisher,
            VariableResolver variableResolver,
            AssertionEvaluator assertionEvaluator) {
        this.channels          = List.copyOf(channels);
        this.eventPublisher    = eventPublisher;
        this.variableResolver  = variableResolver;
        this.assertionEvaluator = assertionEvaluator;
    }

    public RunExecution.ScenarioExecution executeScenario(
            TestScenario scenario,
            Map<String, Object> context,
            UUID executionId) {

        eventPublisher.publishScenarioStarted(executionId, scenario.id(), 0);
        // Merge scenario-level variables into context (run-level variables take precedence if set)
        Map<String, Object> ctx = new HashMap<>(scenario.variables());
        ctx.putAll(context);  // run-level and runtime overrides win

        List<RunExecution.StepExecution> stepExecutions = new ArrayList<>();
        boolean failFastTriggered = false;
        int stepIndex = 0;

        for (TestStep step : scenario.steps()) {
            String stepId = stepIdOf(step, stepIndex);
            if (failFastTriggered) {
                StepResult skipped = new StepResult.Skipped(stepId, "prior step failed with fail_fast=true");
                stepExecutions.add(new RunExecution.StepExecution(stepId, stepIndex, skipped, Instant.now()));
                stepIndex++;
                continue;
            }

            eventPublisher.publishStepStarted(executionId, stepId, describeStep(step));
            Instant start = Instant.now();

            StepResult result = executeStep(step, ctx, executionId, stepId, start);
            stepExecutions.add(new RunExecution.StepExecution(stepId, stepIndex, result, Instant.now()));
            eventPublisher.publishStepResult(executionId, stepId, result);

            // Merge captured variables back into context for downstream steps
            if (result instanceof StepResult.Passed p && p.captured() != null) {
                ctx.putAll(p.captured());
            }

            if (result instanceof StepResult.Failed f) {
                boolean isFastFail = step instanceof TestStep.Assert a && a.failFast();
                if (isFastFail) failFastTriggered = true;
            }
            if (result instanceof StepResult.TimedOut) {
                failFastTriggered = true;
            }
            stepIndex++;
        }

        // Propagate final context back to caller (run-level shared context)
        context.putAll(ctx);

        boolean scenarioPassed = stepExecutions.stream()
                .noneMatch(se -> se.result() instanceof StepResult.Failed
                              || se.result() instanceof StepResult.TimedOut);

        ExecutionStatus status = scenarioPassed ? ExecutionStatus.PASSED : ExecutionStatus.FAILED;
        eventPublisher.publishScenarioComplete(executionId, scenario.id(), status);
        return new RunExecution.ScenarioExecution(scenario.id(), 0, stepExecutions, status);
    }

    public void resumeWait(UUID executionId, String stepId, ResumeOutcome outcome) {
        String key = key(executionId, stepId);
        CompletableFuture<ResumeOutcome> future = pendingResumes.get(key);
        if (future != null) {
            future.complete(outcome);
        } else {
            log.warn("qa.resume.no_pending_step executionId={} stepId={}", executionId, stepId);
        }
    }

    private StepResult executeStep(TestStep step, Map<String, Object> ctx,
                                   UUID executionId, String stepId, Instant start) {
        return switch (step) {
            case TestStep.Send s           -> executeSend(s, ctx, executionId, stepId, start);
            case TestStep.Assert a         -> executeAssert(a, ctx, stepId, start);
            case TestStep.WaitForHuman w   -> executeWaitForHuman(w, ctx, executionId, start);
            case TestStep.Loop l           -> executeLoop(l, ctx, executionId, start);
            case TestStep.InjectVariable iv -> executeInject(iv, ctx, stepId, start);
        };
    }

    private StepResult executeSend(TestStep.Send step, Map<String, Object> ctx,
                                   UUID executionId, String stepId, Instant start) {
        TestChannelPort adapter = channels.stream()
                .filter(c -> c.supports(step.channel()))
                .findFirst()
                .orElse(null);

        if (adapter == null) {
            return new StepResult.Failed(stepId, "No adapter found for channel: " + step.channel(),
                    null, null, elapsed(start));
        }

        // Resolve variables in payload before sending
        Map<String, Object> resolvedPayload = variableResolver.resolveAll(step.payload(), ctx);
        String resolvedOperation = variableResolver.resolve(step.operation(), ctx);
        TestStep.Send resolved = new TestStep.Send(step.channel(), resolvedOperation, resolvedPayload,
                step.timeout(), step.captureResponseAs());
        try {
            StepResult.Passed result = adapter.execute(resolved, ctx);
            // Merge captured response into context under captureResponseAs key
            if (step.captureResponseAs() != null && result.captured() != null) {
                ctx.put(step.captureResponseAs(), result.captured());
            }
            return result;
        } catch (Exception e) {
            log.error("qa.step.send_failed stepId={} operation={}", stepId, step.operation(), e);
            return new StepResult.Failed(stepId, e.getMessage(), null, null, elapsed(start));
        }
    }

    private StepResult executeAssert(TestStep.Assert step, Map<String, Object> ctx,
                                     String stepId, Instant start) {
        AssertionEvaluator.EvaluationResult result = assertionEvaluator.evaluate(step.expression(), ctx);
        if (result.passed()) {
            return new StepResult.Passed(stepId, elapsed(start), Map.of());
        }
        return new StepResult.Failed(stepId, step.description() + " — " + result.message(),
                result.actual(), null, elapsed(start));
    }

    private StepResult executeWaitForHuman(TestStep.WaitForHuman step, Map<String, Object> ctx,
                                           UUID executionId, Instant start) {
        String key = key(executionId, step.stepId());
        CompletableFuture<ResumeOutcome> future = new CompletableFuture<>();
        pendingResumes.put(key, future);

        Instant expiresAt = Instant.now().plus(step.timeout());
        eventPublisher.publishWaitForHuman(executionId, step.stepId(), step.instruction(), expiresAt);

        try {
            ResumeOutcome outcome = future.get(step.timeout().toMillis(), TimeUnit.MILLISECONDS);
            pendingResumes.remove(key);
            if (outcome == ResumeOutcome.PASS) {
                return new StepResult.Passed(step.stepId(), elapsed(start), Map.of());
            } else {
                return new StepResult.Failed(step.stepId(), "Human marked step as FAIL",
                        null, null, elapsed(start));
            }
        } catch (TimeoutException e) {
            pendingResumes.remove(key);
            return new StepResult.TimedOut(step.stepId(), elapsed(start));
        } catch (InterruptedException | ExecutionException e) {
            pendingResumes.remove(key);
            Thread.currentThread().interrupt();
            return new StepResult.Failed(step.stepId(), "Interrupted: " + e.getMessage(),
                    null, null, elapsed(start));
        }
    }

    private StepResult executeLoop(TestStep.Loop step, Map<String, Object> ctx,
                                   UUID executionId, Instant start) {
        // LEARN: parallel loop — spawns N virtual threads, one per iteration. All share the same
        //        context (read) but write to separate iteration-scoped sub-contexts to avoid races.
        //        Results are collected via List<Future<List<StepResult>>>.
        if (step.parallel()) {
            return executeLoopParallel(step, ctx, executionId, start);
        }
        for (int i = 0; i < step.count(); i++) {
            Map<String, Object> iterCtx = new HashMap<>(ctx);
            iterCtx.put("$loop_index", String.valueOf(i));
            for (TestStep inner : step.steps()) {
                String innerStepId = "loop_" + i + "_" + stepIdOf(inner, i);
                Instant innerStart = Instant.now();
                StepResult result = executeStep(inner, iterCtx, executionId, innerStepId, innerStart);
                if (result instanceof StepResult.Failed || result instanceof StepResult.TimedOut) {
                    return new StepResult.Failed("loop", "Iteration " + i + " failed: " + result,
                            null, null, elapsed(start));
                }
            }
            if (!step.delayBetween().isZero() && i < step.count() - 1) {
                try { Thread.sleep(step.delayBetween()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
        return new StepResult.Passed("loop", elapsed(start), Map.of());
    }

    private StepResult executeLoopParallel(TestStep.Loop step, Map<String, Object> ctx,
                                           UUID executionId, Instant start) {
        List<CompletableFuture<StepResult>> futures = new ArrayList<>();
        for (int i = 0; i < step.count(); i++) {
            final int iteration = i;
            Map<String, Object> iterCtx = new HashMap<>(ctx);
            iterCtx.put("$loop_index", String.valueOf(iteration));
            // LEARN: Thread.ofVirtual() — JDK 21+ virtual thread; cheap to create (stack starts at ~1KB)
            CompletableFuture<StepResult> f = CompletableFuture.supplyAsync(() -> {
                for (TestStep inner : step.steps()) {
                    String innerStepId = "loop_" + iteration + "_" + stepIdOf(inner, iteration);
                    StepResult result = executeStep(inner, iterCtx, executionId, innerStepId, Instant.now());
                    if (result instanceof StepResult.Failed || result instanceof StepResult.TimedOut) {
                        return result;
                    }
                }
                return new StepResult.Passed("loop_" + iteration, elapsed(start), Map.of());
            }, runnable -> Thread.ofVirtual().start(runnable));
            futures.add(f);
        }

        List<StepResult> results = futures.stream()
                .map(f -> { try { return f.get(60, TimeUnit.SECONDS); } catch (Exception e) { return new StepResult.Failed("loop", e.getMessage(), null, null, elapsed(start)); }})
                .toList();

        long failures = results.stream().filter(r -> r instanceof StepResult.Failed || r instanceof StepResult.TimedOut).count();
        if (failures > 0) {
            return new StepResult.Failed("loop", failures + "/" + step.count() + " iterations failed",
                    null, null, elapsed(start));
        }
        return new StepResult.Passed("loop", elapsed(start), Map.of());
    }

    private StepResult executeInject(TestStep.InjectVariable step, Map<String, Object> ctx,
                                     String stepId, Instant start) {
        String resolved = variableResolver.resolve(step.expression(), ctx);
        ctx.put(step.name(), resolved);
        return new StepResult.Passed(stepId, elapsed(start), Map.of(step.name(), resolved));
    }

    private static String stepIdOf(TestStep step, int index) {
        return switch (step) {
            case TestStep.Send s           -> "send_" + index;
            case TestStep.Assert a         -> "assert_" + index;
            case TestStep.WaitForHuman w   -> w.stepId();
            case TestStep.Loop l           -> "loop_" + index;
            case TestStep.InjectVariable iv -> "inject_" + iv.name();
        };
    }

    private static String describeStep(TestStep step) {
        return switch (step) {
            case TestStep.Send s           -> "send " + s.channel() + " " + s.operation();
            case TestStep.Assert a         -> "assert: " + a.description();
            case TestStep.WaitForHuman w   -> "wait_for_human: " + w.instruction();
            case TestStep.Loop l           -> "loop x" + l.count() + (l.parallel() ? " (parallel)" : "");
            case TestStep.InjectVariable iv -> "inject " + iv.name();
        };
    }

    private static Duration elapsed(Instant start) {
        return Duration.between(start, Instant.now());
    }

    private static String key(UUID executionId, String stepId) {
        return executionId + ":" + stepId;
    }
}
