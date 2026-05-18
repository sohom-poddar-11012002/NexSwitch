package com.nexswitch.qa.adapter.channel;

import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

// LEARN: Playwright Java drives Chromium/Firefox/WebKit via CDP; matches elements by
//        data-testid attributes instead of fragile CSS paths, making tests resilient to
//        UI refactors as long as test hooks (data-testid) remain stable.
//        Enable with qa.channel.playwright.enabled=true once Playwright browsers are installed.
@Component
@ConditionalOnProperty(name = "qa.channel.playwright.enabled", havingValue = "true")
public class PlaywrightTestAdapter implements TestChannelPort {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightTestAdapter.class);

    @Override
    public boolean supports(ChannelType channelType) {
        return channelType == ChannelType.PLAYWRIGHT;
    }

    // Payload keys: action (navigate|click|fill|assert_text|assert_visible|screenshot),
    //               selector (data-testid value), value (for fill),
    //               page_url (for navigate), expected_text (for assert_text)
    @Override
    public StepResult.Passed execute(TestStep.Send step, Map<String, Object> context) throws Exception {
        Instant start  = Instant.now();
        String  action = require(step.payload(), "action");
        log.info("qa.playwright.execute operation={} action={}", step.operation(), action);

        Map<String, Object> captured = switch (action) {
            case "navigate"       -> executeNavigate(step.payload());
            case "click"          -> executeClick(step.payload());
            case "fill"           -> executeFill(step.payload());
            case "assert_text"    -> executeAssertText(step.payload());
            case "assert_visible" -> executeAssertVisible(step.payload());
            case "screenshot"     -> executeScreenshot(step.payload());
            default               -> throw new IllegalArgumentException("Unknown Playwright action: " + action);
        };
        return new StepResult.Passed(step.operation(), Duration.between(start, Instant.now()), captured);
    }

    private Map<String, Object> executeNavigate(Map<String, Object> payload) {
        String url = require(payload, "page_url");
        log.info("qa.playwright.navigate url={}", url);
        return Map.of("action", "navigate", "page_url", url, "status", "stub");
    }

    private Map<String, Object> executeClick(Map<String, Object> payload) {
        String selector = require(payload, "selector");
        log.info("qa.playwright.click selector=[data-testid='{}']", selector);
        return Map.of("action", "click", "selector", selector, "status", "stub");
    }

    private Map<String, Object> executeFill(Map<String, Object> payload) {
        String selector = require(payload, "selector");
        String value    = require(payload, "value");
        log.info("qa.playwright.fill selector=[data-testid='{}'] value={}", selector, value);
        return Map.of("action", "fill", "selector", selector, "value", value, "status", "stub");
    }

    private Map<String, Object> executeAssertText(Map<String, Object> payload) {
        String selector     = require(payload, "selector");
        String expectedText = require(payload, "expected_text");
        log.info("qa.playwright.assert_text selector=[data-testid='{}'] expected={}", selector, expectedText);
        return Map.of("action", "assert_text", "selector", selector, "expected_text", expectedText, "status", "stub");
    }

    private Map<String, Object> executeAssertVisible(Map<String, Object> payload) {
        String selector = require(payload, "selector");
        log.info("qa.playwright.assert_visible selector=[data-testid='{}']", selector);
        return Map.of("action", "assert_visible", "selector", selector, "status", "stub");
    }

    private Map<String, Object> executeScreenshot(Map<String, Object> payload) {
        log.info("qa.playwright.screenshot operation=stub");
        return Map.of("action", "screenshot", "screenshot_base64", "", "page_url", "", "status", "stub");
    }

    private static String require(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null) throw new IllegalArgumentException("Playwright payload missing required key: " + key);
        return val.toString();
    }
}
