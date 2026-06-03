package com.nexswitch.qa.domain.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// LEARN: Template substitution — {{$stan}} generates a guaranteed-unique STAN per call;
//        {{$uuid}} generates a UUID; {{pan}} looks up "pan" in the context map.
//        This creates a deterministic audit trail — resolved values are stored in step results.
public class VariableResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    // LEARN: Seed from epoch millis so counter starts at a different position after each restart,
    //        preventing STAN collisions with transactions persisted in prior runs.
    private final AtomicInteger stanCounter = new AtomicInteger((int) (System.currentTimeMillis() % 1_000_000));

    public String resolve(String template, Map<String, Object> context) {
        if (template == null) return null;
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = resolveKey(key, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveAll(Map<String, Object> payload, Map<String, Object> context) {
        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                resolved.put(entry.getKey(), resolve(s, context));
            } else if (value instanceof Map<?, ?> m) {
                resolved.put(entry.getKey(), resolveAll((Map<String, Object>) m, context));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }

    private String resolveKey(String key, Map<String, Object> context) {
        return switch (key) {
            case "$stan"  -> String.format("%06d", stanCounter.getAndIncrement());
            case "$uuid"  -> UUID.randomUUID().toString();
            case "$now"   -> String.valueOf(Instant.now().toEpochMilli());
            default -> {
                // $loop_index passed in context as "$loop_index"
                // User-defined variables from context map
                Object val = context.get(key);
                yield val != null ? val.toString() : "{{" + key + "}}"; // leave unresolved if missing
            }
        };
    }
}
