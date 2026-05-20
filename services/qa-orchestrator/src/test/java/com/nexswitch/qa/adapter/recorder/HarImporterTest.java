package com.nexswitch.qa.adapter.recorder;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarImporterTest {

    private final HarImporter importer = new HarImporter(new ObjectMapper());

    @Test
    void singleGetEntry_generatesRestScenario() throws Exception {
        String har = """
                {
                  "log": {
                    "entries": [{
                      "request": { "method": "GET", "url": "http://localhost:8080/api/transactions/tx-1" },
                      "response": { "status": 200 }
                    }]
                  }
                }
                """;

        String yaml = importer.importHar(stream(har), "test-scenario");

        assertThat(yaml).contains("id: test-scenario");
        assertThat(yaml).contains("channel: REST");
        assertThat(yaml).contains("operation: \"GET /api/transactions/tx-1\"");
        assertThat(yaml).contains("status_code'] == '200'");
    }

    @Test
    void postEntryWithBody_includesPayloadInYaml() throws Exception {
        String har = """
                {
                  "log": {
                    "entries": [{
                      "request": {
                        "method": "POST",
                        "url": "http://localhost:8080/api/payments",
                        "postData": { "mimeType": "application/json", "text": "{\\"amount\\":100}" }
                      },
                      "response": { "status": 201 }
                    }]
                  }
                }
                """;

        String yaml = importer.importHar(stream(har), "payment-har");

        assertThat(yaml).contains("operation: \"POST /api/payments\"");
        assertThat(yaml).contains("body:");
        assertThat(yaml).contains("{\"amount\":100}");
        assertThat(yaml).contains("status_code'] == '201'");
    }

    @Test
    void multipleEntries_generatesStepForEach() throws Exception {
        String har = """
                {
                  "log": {
                    "entries": [
                      { "request": { "method": "GET", "url": "http://host/api/a" }, "response": { "status": 200 } },
                      { "request": { "method": "GET", "url": "http://host/api/b" }, "response": { "status": 404 } }
                    ]
                  }
                }
                """;

        String yaml = importer.importHar(stream(har), "multi-har");

        assertThat(yaml).contains("response_0");
        assertThat(yaml).contains("response_1");
        assertThat(yaml).contains("status_code'] == '200'");
        assertThat(yaml).contains("status_code'] == '404'");
    }

    @Test
    void emptyEntries_throwsIllegalArgument() {
        String har = """
                { "log": { "entries": [] } }
                """;

        assertThatThrownBy(() -> importer.importHar(stream(har), "empty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no request entries");
    }

    @Test
    void missingLogEntries_throwsIllegalArgument() {
        String har = """
                { "log": {} }
                """;

        assertThatThrownBy(() -> importer.importHar(stream(har), "missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ByteArrayInputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
