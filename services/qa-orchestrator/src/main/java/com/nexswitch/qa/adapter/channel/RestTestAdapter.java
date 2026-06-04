package com.nexswitch.qa.adapter.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        String method   = parts[0].toUpperCase();
        String path     = parts[1];

        String baseUrl = resolveBaseUrl(context, step.channel());
        String url     = baseUrl + path;

        String apiKey = resolveApiKey(context);

        String serializedBody = step.payload() != null && !step.payload().isEmpty()
                ? objectMapper.writeValueAsString(step.payload()) : "{}";
        log.info("qa.rest.send method={} url={} payload_keys={} body={}", method, url,
                step.payload() != null ? step.payload().keySet() : "null",
                serializedBody.substring(0, Math.min(200, serializedBody.length())));

        // LEARN: HttpURLConnection with setFixedLengthStreamingMode ensures Content-Length is sent
        //        (not chunked transfer encoding), which is required by uvicorn/h11 for body parsing.
        String responseBody;
        int statusCode;

        if (method.equals("GET") || method.equals("DELETE")) {
            // Use RestClient for read-only methods (no body)
            ResponseEntity<String> resp = method.equals("GET")
                    ? restClient.get().uri(url)
                            .header("X-API-Key", apiKey)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, (req, res) -> {})
                            .toEntity(String.class)
                    : restClient.delete().uri(url)
                            .header("X-API-Key", apiKey)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, (req, res) -> {})
                            .toEntity(String.class);
            statusCode = resp.getStatusCode().value();
            responseBody = resp.getBody();
        } else {
            // Use raw HttpURLConnection for body-bearing methods to guarantee Content-Length header
            byte[] bodyBytes = serializedBody.getBytes(StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            try {
                conn.setRequestMethod(method);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout((int) step.timeout().toMillis());
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                if (!apiKey.isEmpty()) conn.setRequestProperty("X-API-Key", apiKey);
                conn.setFixedLengthStreamingMode(bodyBytes.length);
                conn.connect();
                conn.getOutputStream().write(bodyBytes);
                conn.getOutputStream().flush();
                statusCode = conn.getResponseCode();
                InputStream is = statusCode < 400 ? conn.getInputStream() : conn.getErrorStream();
                responseBody = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
            } finally {
                conn.disconnect();
            }
        }

        Map<String, Object> captured = new HashMap<>();
        captured.put("status_code", String.valueOf(statusCode));
        captured.put("response_body", responseBody);
        if (responseBody != null && (responseBody.startsWith("{") || responseBody.startsWith("["))) {
            captured.put("response_json", responseBody);
            // LEARN: Top-level JSON fields extracted with json_ prefix so downstream inject_variable
            //        steps can reference e.g. {{json_txnRef}} without a full JSONPath expression.
            extractJsonFields(responseBody, captured);
        }

        log.info("qa.rest.done method={} url={} status={} body_preview={}", method, url, statusCode,
                responseBody != null ? responseBody.substring(0, Math.min(100, responseBody.length())) : "null");
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
        Object override = context.get("rest_base_url");
        if (override != null) return override.toString().stripTrailing().replaceAll("/$", "");
        return "";
    }

    private String resolveApiKey(Map<String, Object> context) {
        Object key = context.get("rest_api_key");
        return key != null ? key.toString() : "";
    }
}
