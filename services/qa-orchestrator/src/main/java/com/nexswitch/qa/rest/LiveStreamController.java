package com.nexswitch.qa.rest;

import com.nexswitch.qa.adapter.sse.SseEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/qa/runs")
public class LiveStreamController {

    private final SseEventPublisher ssePublisher;

    public LiveStreamController(SseEventPublisher ssePublisher) {
        this.ssePublisher = ssePublisher;
    }

    // LEARN: text/event-stream — HTTP/1.1 response that never closes; browser EventSource
    //        reads each "data:" line as a message. Spring's SseEmitter writes these lines.
    @GetMapping(value = "/{executionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID executionId) {
        return ssePublisher.subscribe(executionId);
    }
}
