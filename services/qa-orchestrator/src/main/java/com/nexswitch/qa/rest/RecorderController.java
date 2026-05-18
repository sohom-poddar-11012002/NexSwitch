package com.nexswitch.qa.rest;

import com.nexswitch.qa.adapter.recorder.HarImporter;
import com.nexswitch.qa.adapter.recorder.Iso8583RecorderProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/recorder")
public class RecorderController {

    private final HarImporter                  harImporter;
    private final Optional<Iso8583RecorderProxy> proxy;
    private final Path                         recordedDir;

    public RecorderController(
            HarImporter harImporter,
            Optional<Iso8583RecorderProxy> proxy,
            @Value("${qa.scenarios.recorded-dir:recorded}") String recordedDir) {
        this.harImporter = harImporter;
        this.proxy       = proxy;
        this.recordedDir = Path.of(recordedDir);
    }

    @PostMapping("/proxy/start")
    public ResponseEntity<Map<String, Object>> startProxy() throws Exception {
        if (proxy.isEmpty())
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Recorder proxy not enabled — set qa.recorder.proxy.enabled=true"));
        proxy.get().start();
        return ResponseEntity.ok(Map.of("status", "RUNNING", "running", proxy.get().isRunning()));
    }

    @PostMapping("/proxy/stop")
    public ResponseEntity<Map<String, Object>> stopProxy() {
        proxy.ifPresent(Iso8583RecorderProxy::stop);
        return ResponseEntity.ok(Map.of("status", "STOPPED"));
    }

    @GetMapping("/proxy/status")
    public Map<String, Object> proxyStatus() {
        return proxy.<Map<String, Object>>map(p -> Map.of(
                "running",        p.isRunning(),
                "recordingCount", p.getRecordingCount()
        )).orElse(Map.of("running", false, "enabled", false));
    }

    @GetMapping("/recordings")
    public List<Map<String, Object>> listRecordings() throws Exception {
        if (!Files.exists(recordedDir)) return List.of();
        try (var stream = Files.list(recordedDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".yml"))
                    .sorted(Comparator.reverseOrder())
                    .limit(50)
                    .map(p -> Map.<String, Object>of(
                            "filename",  p.getFileName().toString(),
                            "sizeBytes", p.toFile().length()))
                    .collect(Collectors.toList());
        }
    }

    @PostMapping(value = "/import-har", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importHar(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "") String scenarioId) throws Exception {
        String id   = scenarioId.isBlank() ? "har-" + Instant.now().toEpochMilli() : scenarioId;
        String yaml = harImporter.importHar(file.getInputStream(), id);
        return ResponseEntity.ok(Map.of("scenarioId", id, "yaml", yaml));
    }
}
