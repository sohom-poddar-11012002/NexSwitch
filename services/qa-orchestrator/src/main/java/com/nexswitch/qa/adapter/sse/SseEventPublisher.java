package com.nexswitch.qa.adapter.sse;

import com.nexswitch.qa.domain.model.ExecutionStatus;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.port.outbound.ExecutionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// LEARN: SseEmitter — Spring's server-sent event abstraction; one emitter per connected browser tab.
//        Events push to all subscribers for the run, so the portal gets live step updates
//        without polling. SseEmitter.complete() closes the HTTP/1.1 chunked stream.
@Component
public class SseEventPublisher implements ExecutionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseEventPublisher.class);

    // One list of emitters per executionId — multiple browser tabs can subscribe
    private final Map<UUID, CopyOnWriteEmitterList> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID executionId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(executionId, k -> new CopyOnWriteEmitterList()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(executionId, emitter));
        emitter.onTimeout(()    -> removeEmitter(executionId, emitter));
        emitter.onError(e      -> removeEmitter(executionId, emitter));
        return emitter;
    }

    @Override
    public void publishRunStarted(UUID executionId, String runId) {
        broadcast(executionId, "RUN_STARTED", Map.of("runId", runId, "executionId", executionId.toString()));
    }

    @Override
    public void publishScenarioStarted(UUID executionId, String scenarioId, int index) {
        broadcast(executionId, "SCENARIO_STARTED", Map.of("scenarioId", scenarioId, "index", index));
    }

    @Override
    public void publishStepStarted(UUID executionId, String stepId, String description) {
        broadcast(executionId, "STEP_STARTED", Map.of("stepId", stepId, "description", description));
    }

    @Override
    public void publishStepResult(UUID executionId, String stepId, StepResult result) {
        String eventType = switch (result) {
            case StepResult.Passed    p -> "STEP_PASSED";
            case StepResult.Failed    f -> "STEP_FAILED";
            case StepResult.Skipped   s -> "STEP_SKIPPED";
            case StepResult.TimedOut  t -> "STEP_TIMED_OUT";
            case StepResult.WaitingForHuman w -> "WAITING_FOR_HUMAN";
        };
        Map<String, Object> data = switch (result) {
            case StepResult.Passed p    -> Map.of("stepId", stepId, "elapsedMs", p.elapsed().toMillis());
            case StepResult.Failed f    -> Map.of("stepId", stepId, "reason", f.reason(),
                    "actual", f.actualValue() != null ? f.actualValue() : "");
            case StepResult.Skipped s   -> Map.of("stepId", stepId, "reason", s.reason());
            case StepResult.TimedOut t  -> Map.of("stepId", stepId, "elapsedMs", t.elapsed().toMillis());
            case StepResult.WaitingForHuman w -> Map.of("stepId", stepId,
                    "instruction", w.instruction(), "expiresAt", w.expiresAt().toString());
        };
        broadcast(executionId, eventType, data);
    }

    @Override
    public void publishWaitForHuman(UUID executionId, String stepId, String instruction, Instant expiresAt) {
        broadcast(executionId, "WAITING_FOR_HUMAN", Map.of(
                "stepId", stepId, "instruction", instruction, "expiresAt", expiresAt.toString()));
    }

    @Override
    public void publishScenarioComplete(UUID executionId, String scenarioId, ExecutionStatus status) {
        String eventType = status == ExecutionStatus.PASSED ? "SCENARIO_PASSED" : "SCENARIO_FAILED";
        broadcast(executionId, eventType, Map.of("scenarioId", scenarioId, "status", status.name()));
    }

    @Override
    public void publishRunComplete(UUID executionId, ExecutionStatus status) {
        String eventType = status == ExecutionStatus.PASSED ? "RUN_PASSED" : "RUN_FAILED";
        broadcast(executionId, eventType, Map.of("status", status.name(), "executionId", executionId.toString()));
        // Close all emitters for this run — stream is done
        CopyOnWriteEmitterList list = emitters.remove(executionId);
        if (list != null) list.completeAll();
    }

    private void broadcast(UUID executionId, String eventType, Map<String, Object> data) {
        CopyOnWriteEmitterList list = emitters.get(executionId);
        if (list == null) return;
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name(eventType)
                .data(data);
        list.send(event, executionId, eventType);
    }

    private void removeEmitter(UUID executionId, SseEmitter emitter) {
        CopyOnWriteEmitterList list = emitters.get(executionId);
        if (list != null) list.remove(emitter);
    }

    // Thread-safe list of emitters with safe iteration during broadcast
    private static class CopyOnWriteEmitterList {
        private final java.util.concurrent.CopyOnWriteArrayList<SseEmitter> list = new java.util.concurrent.CopyOnWriteArrayList<>();

        void add(SseEmitter e)    { list.add(e); }
        void remove(SseEmitter e) { list.remove(e); }

        void send(SseEmitter.SseEventBuilder event, UUID executionId, String eventType) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(event);
                } catch (IOException ex) {
                    log.warn("qa.sse.send_failed executionId={} event={}", executionId, eventType);
                    list.remove(emitter);
                }
            }
        }

        void completeAll() {
            for (SseEmitter emitter : list) {
                try { emitter.complete(); } catch (Exception ignored) {}
            }
            list.clear();
        }
    }
}
