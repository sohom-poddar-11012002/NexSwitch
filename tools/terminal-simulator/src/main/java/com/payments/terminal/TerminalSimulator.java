package com.payments.terminal;

import com.payments.terminal.config.TerminalConfig;
import com.payments.terminal.scenario.Scenario;
import com.payments.terminal.scenario.ScenarioRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone POS terminal emulator. No Spring Boot. No Spring context.
 * Builds ISO 8583 messages and sends them over TCP to the acquiring-service.
 *
 * Run:    java -jar terminal-simulator.jar [SCENARIO]
 * Config: terminal.yml on classpath (inside the fat JAR) or working directory
 *
 * Scenarios: NORMAL_PURCHASE | OVERLIMIT | TIMEOUT | DUPLICATE
 */
// LEARN: StandaloneJar — maven-shade-plugin merges all deps into one fat JAR; no Spring Boot repackage needed
public class TerminalSimulator {

    private static final Logger log = LoggerFactory.getLogger(TerminalSimulator.class);

    public static void main(String[] args) throws Exception {
        TerminalConfig config   = TerminalConfig.load();
        Scenario       scenario = resolveScenario(args, config);

        log.info("Terminal Simulator — ID={} Host={}:{} Scenario={}",
            config.terminalId(), config.switchHost(), config.switchPort(), scenario);

        new ScenarioRunner(config).run(scenario);
    }

    private static Scenario resolveScenario(String[] args, TerminalConfig config) {
        String name = args.length > 0 ? args[0] : config.scenario();
        try {
            return Scenario.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown scenario '{}'; defaulting to NORMAL_PURCHASE", name);
            return Scenario.NORMAL_PURCHASE;
        }
    }
}
