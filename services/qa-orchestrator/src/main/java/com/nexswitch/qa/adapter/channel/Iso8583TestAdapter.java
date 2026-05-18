package com.nexswitch.qa.adapter.channel;

import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

// LEARN: LengthPrefixedFraming — 4-byte big-endian int before each message body eliminates
//        TCP stream boundary ambiguity; same wire format as terminal-simulator's SwitchTcpClient.
@Component
public class Iso8583TestAdapter implements TestChannelPort {

    private static final Logger log = LoggerFactory.getLogger(Iso8583TestAdapter.class);

    private static final String PACKAGER_CLASSPATH = "cfg/terminal-packager.xml";
    private static final DateTimeFormatter MMDD_HHMMSS = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter HHMMSS      = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter MMDD        = DateTimeFormatter.ofPattern("MMdd");

    private final String switchHost;
    private final int    switchPort;
    private final GenericPackager packager;

    public Iso8583TestAdapter(
            @Value("${qa.channel.iso8583.host:localhost}") String switchHost,
            @Value("${qa.channel.iso8583.port:8000}")      int switchPort) {
        this.switchHost = switchHost;
        this.switchPort = switchPort;
        this.packager   = loadPackager();
    }

    @Override
    public boolean supports(ChannelType channelType) {
        return channelType == ChannelType.ISO8583;
    }

    @Override
    public StepResult.Passed execute(TestStep.Send step, Map<String, Object> context) throws Exception {
        Instant start = Instant.now();
        ISOMsg request  = buildMessage(step.operation(), step.payload());
        ISOMsg response = sendAndReceive(request, step.timeout());

        Map<String, Object> captured = extractFields(response);
        log.info("qa.iso8583.done operation={} field39={}", step.operation(), captured.get("field39"));
        return new StepResult.Passed(step.operation(), Duration.between(start, Instant.now()), captured);
    }

    private ISOMsg buildMessage(String operation, Map<String, Object> payload) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);

        String mti = switch (operation) {
            case "auth_request_0100"     -> "0100";
            case "reversal_request_0400" -> "0400";
            case "network_request_0800"  -> "0800";
            default                      -> operation.length() == 4 ? operation : "0100";
        };
        msg.setMTI(mti);

        LocalDateTime now = LocalDateTime.now();
        msg.set(7,  now.format(MMDD_HHMMSS));
        msg.set(12, now.format(HHMMSS));
        msg.set(13, now.format(MMDD));

        // Map payload keys to ISO 8583 field numbers or well-known names
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key   = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            switch (key) {
                case "pan"              -> msg.set(2,  value);
                case "processing_code"  -> msg.set(3,  value);
                case "amount_paise"     -> msg.set(4,  zeroPad(value, 12));
                case "stan"             -> msg.set(11, zeroPad(value, 6));
                case "mcc"              -> msg.set(18, value);
                case "pos_entry_mode"   -> msg.set(22, value);
                case "pos_condition"    -> msg.set(25, value);
                case "track2"           -> msg.set(35, value);
                case "rrn"              -> msg.set(37, zeroPad(value, 12));
                case "terminal_id"      -> msg.set(41, rightPad(value, 8));
                case "merchant_id"      -> msg.set(42, rightPad(value, 15));
                case "merchant_name"    -> msg.set(43, rightPad(value, 40));
                case "currency"         -> msg.set(49, value);
                case "original_data"    -> msg.set(90, value);
                default -> {
                    // Numeric keys like "field_48" → msg.set(48, ...)
                    if (key.startsWith("field_")) {
                        try { msg.set(Integer.parseInt(key.substring(6)), value); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return msg;
    }

    private ISOMsg sendAndReceive(ISOMsg request, Duration timeout) throws IOException, ISOException {
        int timeoutMs = (int) timeout.toMillis();
        // LEARN: Per-test TCP connection — new socket per scenario step; no pooling needed
        //        because test throughput is low and we want clean isolation between test runs.
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(switchHost, switchPort), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream  in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            byte[] packed = request.pack();
            out.writeInt(packed.length);
            out.write(packed);
            out.flush();

            int    responseLen   = in.readInt();
            byte[] responseBytes = in.readNBytes(responseLen);

            ISOMsg response = new ISOMsg();
            response.setPackager(packager);
            response.unpack(responseBytes);
            return response;
        }
    }

    private Map<String, Object> extractFields(ISOMsg response) throws ISOException {
        Map<String, Object> fields = new HashMap<>();
        fields.put("mti", response.getMTI());
        // Extract all present fields as field1, field2 ... field128
        for (int i = 1; i <= 128; i++) {
            if (response.hasField(i)) {
                fields.put("field" + i, response.getString(i));
            }
        }
        // Aliases for common assertion expressions
        if (response.hasField(39)) fields.put("field39", response.getString(39));
        if (response.hasField(38)) fields.put("approval_code", response.getString(38));
        if (response.hasField(37)) fields.put("rrn", response.getString(37));
        return fields;
    }

    private GenericPackager loadPackager() {
        try (var in = Iso8583TestAdapter.class.getClassLoader().getResourceAsStream(PACKAGER_CLASSPATH)) {
            if (in == null) throw new IllegalStateException("terminal-packager.xml not found: " + PACKAGER_CLASSPATH);
            return new GenericPackager(in);
        } catch (ISOException | IOException e) {
            throw new IllegalStateException("Failed to load ISO 8583 packager", e);
        }
    }

    private static String zeroPad(String value, int length) {
        if (value.length() >= length) return value.substring(value.length() - length);
        return "0".repeat(length - value.length()) + value;
    }

    private static String rightPad(String value, int length) {
        if (value.length() >= length) return value.substring(0, length);
        return value + " ".repeat(length - value.length());
    }
}
