package com.nexswitch.qa.adapter.recorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;

// LEARN: HAR (HTTP Archive) — Chrome/Firefox DevTools export format; one JSON file contains
//        every captured HTTP request and response, enabling replay as automated REST scenarios.
@Component
public class HarImporter {

    private final ObjectMapper mapper;

    public HarImporter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String importHar(InputStream harStream, String scenarioId) throws Exception {
        JsonNode root    = mapper.readTree(harStream);
        JsonNode entries = root.path("log").path("entries");
        if (!entries.isArray() || entries.isEmpty())
            throw new IllegalArgumentException("HAR file has no request entries");

        StringBuilder sb = new StringBuilder(512);
        sb.append("scenario:\n")
          .append("  id: ").append(scenarioId).append("\n")
          .append("  name: \"Imported HAR ").append(scenarioId).append("\"\n")
          .append("  description: \"Scenario imported from browser HAR capture\"\n")
          .append("  platform: acquiring-service\n")
          .append("  project: payments\n")
          .append("  feature: recorded\n")
          .append("  channel: REST\n\n")
          .append("  steps:\n");

        int idx = 0;
        for (JsonNode entry : entries) {
            JsonNode req    = entry.path("request");
            JsonNode resp   = entry.path("response");
            String   method = req.path("method").asText("GET").toUpperCase();
            String   url    = req.path("url").asText("");
            String   path   = extractPath(url);
            int      status = resp.path("status").asInt(200);
            String   capKey = "response_" + idx;

            sb.append("    - type: send\n")
              .append("      channel: REST\n")
              .append("      operation: \"").append(method).append(" ").append(path).append("\"\n");

            JsonNode postData = req.path("postData");
            if (!postData.isMissingNode() && postData.has("text")) {
                String body = postData.path("text").asText("").trim();
                if (!body.isEmpty()) {
                    sb.append("      payload:\n")
                      .append("        body: '").append(body.replace("'", "''")).append("'\n");
                }
            }
            sb.append("      timeout_ms: 10000\n")
              .append("      capture_response_as: ").append(capKey).append("\n\n")
              .append("    - type: assert\n")
              .append("      expression: \"").append(capKey)
              .append("['status_code'] == '").append(status).append("'\"\n")
              .append("      description: \"").append(method).append(" ").append(path)
              .append(" should return ").append(status).append("\"\n")
              .append("      fail_fast: false\n\n");
            idx++;
        }
        return sb.toString();
    }

    private String extractPath(String url) {
        try {
            String path = URI.create(url).getPath();
            return path != null && !path.isBlank() ? path : "/";
        } catch (Exception e) {
            int q     = url.indexOf('?');
            String base  = q > 0 ? url.substring(0, q) : url;
            int slash = base.indexOf('/', base.indexOf("://") + 3);
            return slash >= 0 ? base.substring(slash) : "/";
        }
    }
}
