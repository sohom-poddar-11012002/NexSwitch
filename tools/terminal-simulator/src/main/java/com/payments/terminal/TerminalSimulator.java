package com.payments.terminal;

import com.payments.terminal.config.TerminalConfig;

/**
 * Standalone POS terminal emulator. No Spring Boot. No Spring context.
 * Builds and sends ISO 8583 messages over TCP to acquiring-service.
 *
 * Run: java -jar terminal-simulator.jar [scenario]
 * Config: terminal.yml in working directory or classpath
 */
public class TerminalSimulator {

    public static void main(String[] args) throws Exception {
        TerminalConfig config = TerminalConfig.load();
        System.out.printf("Terminal Simulator starting — ID: %s, Host: %s:%d, Scenario: %s%n",
            config.terminalId(), config.switchHost(), config.switchPort(), config.scenario());
        // Implementation in Week 2 — see CLAUDE.md §4.1
    }
}
