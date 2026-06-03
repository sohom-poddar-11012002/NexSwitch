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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// LEARN: SseEmitter — Spring's server-sent event abstraction; one emitter per connected browser tab.
//        Events push to all subscribers for the run, so the portal gets live step updates
//        without polling. SseEmitter.complete() closes the HTTP/1.1 chunked stream.
@Component
public class SseEventPublisher implements ExecutionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseEventPublisher.class);

    // One list of emitters per executionId — multiple browser tabs can subscribe
    private final Map<UUID, CopyOnWriteEmitterList> emitters = new ConcurrentHashMap<>();

    // LEARN: Event replay buffer — stores all events for each run so late subscribers
    //        (browser navigated to /live after events fired) see the full history.
    //        Capped at 500 events per run to bound memory usage.
    private final Map<UUID, List<Map<String, Object>>> replayBuffers = new ConcurrentHashMap<>();
    private static final int MAX_REPLAY = 500;

    public SseEmitter subscribe(UUID executionId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(executionId, k -> new CopyOnWriteEmitterList()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(executionId, emitter));
        emitter.onTimeout(()    -> removeEmitter(executionId, emitter));
        emitter.onError(e      -> removeEmitter(executionId, emitter));
        // LEARN: Tomcat buffers the HTTP response until the first byte is written.
        //        Sending an SSE comment immediately forces Tomcat to flush the 200 headers
        //        so the browser's EventSource sees the connection as established right away.
        try { emitter.send(SseEmitter.event().comment("connected")); } catch (IOException ignored) {}
        // Replay buffered events so late subscribers see the full run history
        List<Map<String, Object>> buffered = replayBuffers.getOrDefault(executionId, List.of());
        for (Map<String, Object> payload : buffered) {
            try { emitter.send(SseEmitter.event().data(payload)); } catch (IOException ignored) { break; }
        }
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
        // Keep replay buffer alive for 60 s so late-loading browser tabs can catch up,
        // then evict to free memory.
        Thread.ofVirtual().start(() -> {
            try { Thread.sleep(60_000); } catch (InterruptedException ignored) {}
            replayBuffers.remove(executionId);
        });
    }

    @Override
    public void publishSuiteStarted(UUID suiteExecutionId, String suiteId) {
        broadcast(suiteExecutionId, "SUITE_STARTED",
                Map.of("suiteId", suiteId, "suiteExecutionId", suiteExecutionId.toString()));
    }

    @Override
    public void publishSuiteComplete(UUID suiteExecutionId, ExecutionStatus status, int passed, int failed) {
        String eventType = status == ExecutionStatus.PASSED ? "SUITE_PASSED" : "SUITE_FAILED";
        broadcast(suiteExecutionId, eventType, Map.of(
                "status", status.name(), "passed", passed, "failed", failed,
                "suiteExecutionId", suiteExecutionId.toString()));
        CopyOnWriteEmitterList list = emitters.remove(suiteExecutionId);
        if (list != null) list.completeAll();
    }

    private void broadcast(UUID executionId, String eventType, Map<String, Object> data) {
        // LEARN: EventSource.onmessage only fires for unnamed SSE events (no "event:" field).
        //        Named events require addEventListener('eventType', ...) — instead we embed
        //        the type in the JSON body so the single onmessage handler sees everything.
        Map<String, Object> payload = new java.util.HashMap<>(data);
        payload.put("type", eventType);
        // Buffer for late subscribers
        replayBuffers.computeIfAbsent(executionId, k -> Collections.synchronizedList(new ArrayList<>()))
                     .add(payload);
        // Cap replay buffer size
        List<Map<String, Object>> buf = replayBuffers.get(executionId);
        if (buf != null && buf.size() > MAX_REPLAY) buf.remove(0);

        CopyOnWriteEmitterList list = emitters.get(executionId);
        if (list == null) return;
        SseEmitter.SseEventBuilder event = SseEmitter.event().data(payload);
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
