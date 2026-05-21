package com.nexswitch.qa.adapter.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// LEARN: RestClient (Spring 6.1+) — fluent, synchronous HTTP client replacing RestTemplate;
//        exchange() gives full ResponseEntity access (status + headers + body).
@Component
public class RestTestAdapter implements TestChannelPort {

    private static final Logger log = LoggerFactory.getLogger(RestTestAdapter.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestTestAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean supports(ChannelType channelType) {
        return channelType == ChannelType.REST;
    }

    // operation format: "METHOD /path" e.g. "GET /api/transactions/123" or "POST /api/payments"
    @Override
    public StepResult.Passed execute(TestStep.Send step, Map<String, Object> context) throws Exception {
        Instant start = Instant.now();
        String[] parts  = parseOperation(step.operation());
        String method   = parts[0];
        String path     = parts[1];

        String baseUrl = resolveBaseUrl(context, step.channel());
        String url     = baseUrl + path;

        ResponseEntity<String> response = switch (method.toUpperCase()) {
            case "GET"    -> restClient.get().uri(url).retrieve().toEntity(String.class);
            case "DELETE" -> restClient.delete().uri(url).retrieve().toEntity(String.class);
            case "POST"   -> restClient.post().uri(url)
                                .body(step.payload())
                                .retrieve().toEntity(String.class);
            case "PUT"    -> restClient.put().uri(url)
                                .body(step.payload())
                                .retrieve().toEntity(String.class);
            case "PATCH"  -> restClient.patch().uri(url)
                                .body(step.payload())
                                .retrieve().toEntity(String.class);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };

        Map<String, Object> captured = new HashMap<>();
        captured.put("status_code", String.valueOf(response.getStatusCode().value()));
        captured.put("response_body", response.getBody());
        String body = response.getBody();
        if (body != null && (body.startsWith("{") || body.startsWith("["))) {
            captured.put("response_json", body);
            // LEARN: Top-level JSON fields extracted with json_ prefix so downstream inject_variable
            //        steps can reference e.g. {{json_txnRef}} without a full JSONPath expression.
            extractJsonFields(body, captured);
        }

        log.info("qa.rest.done method={} url={} status={}", method, url, response.getStatusCode().value());
        return new StepResult.Passed(step.operation(), Duration.between(start, Instant.now()), captured);
    }

    private void extractJsonFields(String json, Map<String, Object> captured) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            parsed.forEach((k, v) -> {
                if (v != null) captured.put("json_" + k, String.valueOf(v));
            });
        } catch (Exception ignored) {}
    }

    private String[] parseOperation(String operation) {
        // "GET /api/foo" or "POST /api/bar"
        int space = operation.indexOf(' ');
        if (space < 0) throw new IllegalArgumentException("REST operation must be 'METHOD /path': " + operation);
        return new String[]{ operation.substring(0, space).trim(), operation.substring(space + 1).trim() };
    }

    private String resolveBaseUrl(Map<String, Object> context, ChannelType channel) {
        // env_profile overrides (injected via context by TriggerRunUseCase)
        Object override = context.get("rest_base_url");
        if (override != null) return override.toString().stripTrailing().replaceAll("/$", "");
        return "";
    }
}
