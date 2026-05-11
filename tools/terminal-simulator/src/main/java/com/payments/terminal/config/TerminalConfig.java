package com.payments.terminal.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public record TerminalConfig(
    String terminalId,
    String merchantId,
    String switchHost,
    int switchPort,
    int timeoutMs,
    String scenario
) {
    @SuppressWarnings("unchecked")
    public static TerminalConfig load() {
        Yaml yaml = new Yaml();
        try (InputStream in = TerminalConfig.class.getClassLoader().getResourceAsStream("terminal.yml")) {
            if (in == null) throw new IllegalStateException("terminal.yml not found on classpath");
            Map<String, Object> root = yaml.load(in);
            Map<String, Object> terminal = (Map<String, Object>) root.get("terminal");
            return new TerminalConfig(
                (String) terminal.get("id"),
                (String) terminal.get("merchant-id"),
                (String) terminal.get("switch-host"),
                (int) terminal.get("switch-port"),
                (int) terminal.getOrDefault("timeout-ms", 15000),
                (String) terminal.getOrDefault("scenario", "NORMAL_PURCHASE")
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load terminal.yml", e);
        }
    }
}
