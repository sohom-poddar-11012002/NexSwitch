package com.payments.terminal.scenario;

import com.payments.terminal.config.TerminalConfig;
import com.payments.terminal.message.Iso8583Builder;
import com.payments.terminal.message.StanGenerator;
import com.payments.terminal.network.SwitchTcpClient;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;

// LEARN: ScenarioPattern — isolates each test scenario behind a named method; switch calls on #16 acquiring-service
public final class ScenarioRunner {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRunner.class);

    private static final int TIMEOUT_SCENARIO_MS = 1_000; // deliberately short to trigger timeout path

    private final TerminalConfig   config;
    private final Iso8583Builder   builder;
    private final StanGenerator    stan;

    public ScenarioRunner(TerminalConfig config) {
        this.config  = config;
        this.builder = new Iso8583Builder();
        this.stan    = new StanGenerator();
    }

    public void run(Scenario scenario) throws IOException, ISOException {
        log.info("Scenario={} TerminalId={} Host={}:{}", scenario, config.terminalId(), config.switchHost(), config.switchPort());
        switch (scenario) {
            case NORMAL_PURCHASE -> runPurchase(stan.next(), scenario.amountPaise());
            case OVERLIMIT       -> runPurchase(stan.next(), scenario.amountPaise());
            case TIMEOUT         -> runWithTimeout(stan.next(), scenario.amountPaise());
            case DUPLICATE       -> runDuplicate(scenario.amountPaise());
        }
    }

    private void runPurchase(String stanValue, String amountPaise) throws IOException, ISOException {
        ISOMsg request = builder.buildAuthRequest(config, stanValue, amountPaise);
        try (SwitchTcpClient client = new SwitchTcpClient(config.switchHost(), config.switchPort(), config.timeoutMs())) {
            ISOMsg response = client.send(request);
            logResponse("0100", stanValue, response);
        }
    }

    private void runWithTimeout(String stanValue, String amountPaise) throws IOException, ISOException {
        ISOMsg request = builder.buildAuthRequest(config, stanValue, amountPaise);
        try (SwitchTcpClient client = new SwitchTcpClient(config.switchHost(), config.switchPort(), TIMEOUT_SCENARIO_MS)) {
            ISOMsg response = client.send(request);
            logResponse("0100", stanValue, response);
        } catch (SocketTimeoutException e) {
            log.warn("TIMEOUT — STAN={} no 0110 received within {}ms; sending 0400 reversal", stanValue, TIMEOUT_SCENARIO_MS);
            sendReversal(stanValue, amountPaise);
        }
    }

    private void runDuplicate(String amountPaise) throws IOException, ISOException {
        String stanValue = stan.next();
        ISOMsg request = builder.buildAuthRequest(config, stanValue, amountPaise);

        try (SwitchTcpClient first = new SwitchTcpClient(config.switchHost(), config.switchPort(), config.timeoutMs())) {
            ISOMsg response = first.send(request);
            logResponse("0100 (first)", stanValue, response);
        }

        log.info("DUPLICATE — resending STAN={} unchanged", stanValue);
        try (SwitchTcpClient second = new SwitchTcpClient(config.switchHost(), config.switchPort(), config.timeoutMs())) {
            ISOMsg response = second.send(request);
            logResponse("0100 (duplicate)", stanValue, response);
        }
    }

    private void sendReversal(String originalStan, String amountPaise) throws IOException, ISOException {
        String reversalStan = stan.next();
        ISOMsg reversal = builder.buildReversalRequest(config, reversalStan, amountPaise, originalStan);
        try (SwitchTcpClient client = new SwitchTcpClient(config.switchHost(), config.switchPort(), config.timeoutMs())) {
            ISOMsg response = client.send(reversal);
            logResponse("0400", reversalStan, response);
        }
    }

    private static void logResponse(String context, String stanValue, ISOMsg response) throws ISOException {
        String mti      = response.getMTI();
        String rc       = response.getString(39);
        String authCode = response.getString(38);
        log.info("{} STAN={} → MTI={} RC={} AuthCode={}", context, stanValue, mti, rc, authCode != null ? authCode.trim() : "—");
    }
}
