package com.nexswitch.qa.adapter.recorder;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// LEARN: TCP MITM proxy — accepts connections on port 8001, creates upstream socket to
//        acquiring-service:8000, relays length-prefixed ISO 8583 frames bidirectionally,
//        captures each request/response pair and serialises to scenario YAML.
@Component
@ConditionalOnProperty(name = "qa.recorder.proxy.enabled", havingValue = "true")
public class Iso8583RecorderProxy {

    private static final Logger log = LoggerFactory.getLogger(Iso8583RecorderProxy.class);
    private static final String PACKAGER_CLASSPATH = "cfg/terminal-packager.xml";

    private final int    proxyPort;
    private final String upstreamHost;
    private final int    upstreamPort;
    private final Path   recordedDir;
    private final GenericPackager packager;

    private final AtomicBoolean running        = new AtomicBoolean(false);
    private final AtomicInteger recordingCount = new AtomicInteger(0);
    private volatile ServerSocket serverSocket;

    public Iso8583RecorderProxy(
            @Value("${qa.recorder.proxy.port:8001}")       int proxyPort,
            @Value("${qa.channel.iso8583.host:localhost}") String upstreamHost,
            @Value("${qa.channel.iso8583.port:8000}")      int upstreamPort,
            @Value("${qa.scenarios.recorded-dir:recorded}") String recordedDir) {
        this.proxyPort    = proxyPort;
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
        this.recordedDir  = Path.of(recordedDir);
        this.packager     = loadPackager();
    }

    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            Files.createDirectories(recordedDir);
            serverSocket = new ServerSocket(proxyPort);
            Thread.ofVirtual().name("recorder-accept").start(this::acceptLoop);
            log.info("qa.recorder.started port={} upstream={}:{}", proxyPort, upstreamHost, upstreamPort);
        }
    }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            log.warn("qa.recorder.stop_error err={}", e.getMessage());
        }
        log.info("qa.recorder.stopped recordings={}", recordingCount.get());
    }

    public boolean isRunning()        { return running.get(); }
    public int     getRecordingCount() { return recordingCount.get(); }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                Thread.ofVirtual()
                      .name("recorder-session-" + recordingCount.incrementAndGet())
                      .start(() -> handleSession(client));
            } catch (IOException e) {
                if (running.get()) log.warn("qa.recorder.accept_error err={}", e.getMessage());
            }
        }
    }

    private void handleSession(Socket client) {
        try (client; Socket upstream = new Socket(upstreamHost, upstreamPort)) {
            DataInputStream  clientIn  = new DataInputStream(new BufferedInputStream(client.getInputStream()));
            DataOutputStream clientOut = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()));
            DataInputStream  upIn      = new DataInputStream(new BufferedInputStream(upstream.getInputStream()));
            DataOutputStream upOut     = new DataOutputStream(new BufferedOutputStream(upstream.getOutputStream()));

            int    reqLen   = clientIn.readInt();
            byte[] reqBytes = clientIn.readNBytes(reqLen);

            upOut.writeInt(reqLen);
            upOut.write(reqBytes);
            upOut.flush();

            int    respLen   = upIn.readInt();
            byte[] respBytes = upIn.readNBytes(respLen);

            clientOut.writeInt(respLen);
            clientOut.write(respBytes);
            clientOut.flush();

            captureAndWrite(reqBytes, respBytes);
        } catch (Exception e) {
            log.warn("qa.recorder.session_error err={}", e.getMessage());
        }
    }

    private void captureAndWrite(byte[] requestBytes, byte[] responseBytes) {
        try {
            ISOMsg request  = parseFrame(requestBytes);
            ISOMsg response = parseFrame(responseBytes);
            String yaml     = generateScenarioYaml(request, response);
            String filename = "recorded-" + Instant.now().toEpochMilli() + ".yml";
            Files.writeString(recordedDir.resolve(filename), yaml);
            log.info("qa.recorder.captured file={}", filename);
        } catch (Exception e) {
            log.warn("qa.recorder.capture_error err={}", e.getMessage());
        }
    }

    private ISOMsg parseFrame(byte[] bytes) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.unpack(bytes);
        return msg;
    }

    String generateScenarioYaml(ISOMsg request, ISOMsg response) throws ISOException {
        String mti = request.getMTI();
        String operation = switch (mti) {
            case "0100" -> "auth_request_0100";
            case "0400" -> "reversal_request_0400";
            case "0800" -> "network_request_0800";
            default     -> mti;
        };
        String scenarioId = "recorded-" + Instant.now().toEpochMilli();
        String pan      = field(request,   2, "");
        String amount   = field(request,   4, "000000000000");
        String termId   = field(request,  41, "").strip();
        String merch    = field(request,  42, "").strip();
        String curr     = field(request,  49, "356");
        String posEntry = field(request,  22, "");
        String field39  = field(response, 39, "");

        StringBuilder sb = new StringBuilder(512);
        sb.append("scenario:\n")
          .append("  id: ").append(scenarioId).append("\n")
          .append("  name: \"Recorded ").append(operation).append("\"\n")
          .append("  description: \"Auto-recorded ISO 8583 session\"\n")
          .append("  platform: acquiring-service\n")
          .append("  project: payments\n")
          .append("  feature: recorded\n")
          .append("  channel: ISO8583\n\n")
          .append("  variables:\n")
          .append("    pan: \"").append(pan).append("\"\n")
          .append("    amount_paise: \"").append(amount).append("\"\n")
          .append("    terminal_id: \"").append(termId).append("\"\n")
          .append("    currency: \"").append(curr).append("\"\n\n")
          .append("  steps:\n")
          .append("    - type: inject_variable\n")
          .append("      name: stan\n")
          .append("      value: \"{{$stan}}\"\n\n")
          .append("    - type: send\n")
          .append("      channel: ISO8583\n")
          .append("      operation: ").append(operation).append("\n")
          .append("      payload:\n")
          .append("        pan: \"{{pan}}\"\n")
          .append("        stan: \"{{stan}}\"\n")
          .append("        amount_paise: \"{{amount_paise}}\"\n")
          .append("        terminal_id: \"{{terminal_id}}\"\n");
        if (!merch.isBlank())    sb.append("        merchant_id: \"").append(merch).append("\"\n");
        sb.append("        currency: \"{{currency}}\"\n");
        if (!posEntry.isBlank()) sb.append("        pos_entry_mode: \"").append(posEntry).append("\"\n");
        sb.append("      timeout_ms: 15000\n")
          .append("      capture_response_as: auth_response\n\n")
          .append("    - type: assert\n")
          .append("      expression: \"auth_response['field39'] == '").append(field39).append("'\"\n")
          .append("      description: \"Expected response code ").append(field39).append("\"\n")
          .append("      fail_fast: true\n");
        return sb.toString();
    }

    private static String field(ISOMsg msg, int f, String def) {
        try { return msg.hasField(f) ? msg.getString(f) : def; }
        catch (Exception e) { return def; }
    }

    private GenericPackager loadPackager() {
        try (var in = getClass().getClassLoader().getResourceAsStream(PACKAGER_CLASSPATH)) {
            if (in == null) throw new IllegalStateException("terminal-packager.xml not found on classpath: " + PACKAGER_CLASSPATH);
            return new GenericPackager(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ISO 8583 packager", e);
        }
    }
}
