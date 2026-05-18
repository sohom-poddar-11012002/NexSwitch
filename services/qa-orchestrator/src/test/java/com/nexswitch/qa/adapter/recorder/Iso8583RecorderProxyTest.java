package com.nexswitch.qa.adapter.recorder;

import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Iso8583RecorderProxyTest {

    // Package-private constructor accepting Path lets us skip loading the packager in unit tests
    private Iso8583RecorderProxy proxy(Path dir) {
        return new Iso8583RecorderProxy(8099, "localhost", 8000, dir.toString());
    }

    @Test
    void initialState_notRunning(@TempDir Path dir) {
        assertThat(proxy(dir).isRunning()).isFalse();
    }

    @Test
    void generateScenarioYaml_authApproved_containsExpectedFields(@TempDir Path dir) throws Exception {
        Iso8583RecorderProxy p = proxy(dir);

        ISOMsg request  = request0100("4539148803436467", "000000100000", "TERM0042", "356", "051");
        ISOMsg response = response0110("00");

        String yaml = p.generateScenarioYaml(request, response);

        assertThat(yaml).contains("channel: ISO8583");
        assertThat(yaml).contains("operation: auth_request_0100");
        assertThat(yaml).contains("pan: \"4539148803436467\"");
        assertThat(yaml).contains("amount_paise: \"000000100000\"");
        assertThat(yaml).contains("terminal_id: \"TERM0042\"");
        assertThat(yaml).contains("currency: \"356\"");
        assertThat(yaml).contains("pos_entry_mode: \"051\"");
        assertThat(yaml).contains("auth_response['field39'] == '00'");
    }

    @Test
    void generateScenarioYaml_declinedResponse_containsDeclineCode(@TempDir Path dir) throws Exception {
        Iso8583RecorderProxy p = proxy(dir);

        ISOMsg request  = request0100("5555555555554444", "000000500000", "TERM0099", "840", "");
        ISOMsg response = response0110("51");

        String yaml = p.generateScenarioYaml(request, response);

        assertThat(yaml).contains("auth_response['field39'] == '51'");
        assertThat(yaml).doesNotContain("pos_entry_mode");
    }

    @Test
    void generateScenarioYaml_reversalMti_mapsToReversalOperation(@TempDir Path dir) throws Exception {
        Iso8583RecorderProxy p = proxy(dir);

        ISOMsg request  = new ISOMsg();
        request.setMTI("0400");
        ISOMsg response = new ISOMsg();
        response.setMTI("0410");

        String yaml = p.generateScenarioYaml(request, response);

        assertThat(yaml).contains("operation: reversal_request_0400");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ISOMsg request0100(String pan, String amount, String termId, String currency, String posEntry)
            throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0100");
        msg.set(2,  pan);
        msg.set(4,  amount);
        msg.set(41, termId);
        msg.set(49, currency);
        if (!posEntry.isBlank()) msg.set(22, posEntry);
        return msg;
    }

    private ISOMsg response0110(String field39) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0110");
        msg.set(39, field39);
        return msg;
    }
}
