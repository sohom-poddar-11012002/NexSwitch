package com.nexswitch.qa.adapter.channel;

import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

// LEARN: ChaosTestAdapter — three fault-injection mechanisms:
//   1. docker pause/unpause  — kills container networking to simulate infra failures
//   2. WireMock stub inject  — injects delay/error responses into mock-upstream at test time
//   3. Spring Actuator CB    — forces circuit breaker state transitions via management endpoint
@Component
public class ChaosTestAdapter implements TestChannelPort {

    private static final Logger log = LoggerFactory.getLogger(ChaosTestAdapter.class);

    // LEARN: CommandRunner interface — production uses ProcessBuilder (runs `docker pause`);
    //        tests inject a mock to verify the right command was issued without touching Docker.
    @FunctionalInterface
    public interface CommandRunner {
        int run(String... args) throws Exception;
    }

    private final RestClient    restClient;
    private final CommandRunner commandRunner;

    public ChaosTestAdapter() {
        this.restClient    = RestClient.create();
        this.commandRunner = args -> {
            Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
            return p.waitFor();
        };
    }

    ChaosTestAdapter(CommandRunner commandRunner) {
        this.restClient    = RestClient.create();
        this.commandRunner = commandRunner;
    }

    @Override
    public boolean supports(ChannelType channelType) {
        return channelType == ChannelType.CHAOS;
    }

    // Supported operations and their payload keys:
    //   docker_pause        container
    //   docker_unpause      container
    //   sleep_ms            duration_ms
    //   wiremock_inject     wiremock_url, mapping (JSON string)
    //   wiremock_reset      wiremock_url
    //   actuator_cb_open    service_url, name
    //   actuator_cb_reset   service_url, name
    @Override
    public StepResult.Passed execute(TestStep.Send step, Map<String, Object> context) throws Exception {
        Instant start = Instant.now();
        Map<String, Object> p = step.payload();

        switch (step.operation()) {
            case "docker_pause" -> {
                String container = require(p, "container");
                int exit = commandRunner.run("docker", "pause", container);
                log.info("qa.chaos.docker_pause container={} exit={}", container, exit);
                if (exit != 0) throw new IOException("docker pause failed exit=" + exit + " container=" + container);
            }
            case "docker_unpause" -> {
                String container = require(p, "container");
                int exit = commandRunner.run("docker", "unpause", container);
                log.info("qa.chaos.docker_unpause container={} exit={}", container, exit);
                if (exit != 0) throw new IOException("docker unpause failed exit=" + exit + " container=" + container);
            }
            case "sleep_ms" -> {
                long durationMs = Long.parseLong(require(p, "duration_ms"));
                log.info("qa.chaos.sleep_ms duration={}", durationMs);
                Thread.sleep(durationMs);
            }
            case "wiremock_inject" -> {
                String url     = require(p, "wiremock_url");
                String mapping = require(p, "mapping");
                restClient.post()
                        .uri(url + "/__admin/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapping)
                        .retrieve()
                        .toBodilessEntity();
                log.info("qa.chaos.wiremock_inject url={}", url);
            }
            case "wiremock_reset" -> {
                String url = require(p, "wiremock_url");
                restClient.post()
                        .uri(url + "/__admin/mappings/reset")
                        .retrieve()
                        .toBodilessEntity();
                log.info("qa.chaos.wiremock_reset url={}", url);
            }
            case "actuator_cb_open" -> {
                String serviceUrl = require(p, "service_url");
                String cbName     = require(p, "name");
                restClient.post()
                        .uri(serviceUrl + "/actuator/circuitbreakers/" + cbName + "/force-open")
                        .retrieve()
                        .toBodilessEntity();
                log.info("qa.chaos.cb_force_open service={} cb={}", serviceUrl, cbName);
            }
            case "actuator_cb_reset" -> {
                String serviceUrl = require(p, "service_url");
                String cbName     = require(p, "name");
                restClient.post()
                        .uri(serviceUrl + "/actuator/circuitbreakers/" + cbName + "/reset")
                        .retrieve()
                        .toBodilessEntity();
                log.info("qa.chaos.cb_reset service={} cb={}", serviceUrl, cbName);
            }
            default -> throw new IllegalArgumentException("Unknown chaos operation: " + step.operation());
        }

        return new StepResult.Passed(step.operation(), Duration.between(start, Instant.now()), Map.of());
    }

    private String require(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) throw new IllegalArgumentException("ChaosTestAdapter: missing payload key '" + key + "'");
        return v.toString();
    }
}
