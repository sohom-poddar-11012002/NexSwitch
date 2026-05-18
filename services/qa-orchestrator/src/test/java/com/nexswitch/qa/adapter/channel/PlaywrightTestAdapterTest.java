package com.nexswitch.qa.adapter.channel;

import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaywrightTestAdapterTest {

    private final PlaywrightTestAdapter adapter = new PlaywrightTestAdapter();

    @Test
    void supportsPlaywrightOnly() {
        assertThat(adapter.supports(ChannelType.PLAYWRIGHT)).isTrue();
        assertThat(adapter.supports(ChannelType.ISO8583)).isFalse();
        assertThat(adapter.supports(ChannelType.REST)).isFalse();
    }

    @Test
    void navigate_returnsPassedWithPageUrl() throws Exception {
        TestStep.Send step = step("navigate", Map.of("action", "navigate", "page_url", "http://localhost:3003/runs"));
        StepResult.Passed result = adapter.execute(step, Map.of());
        assertThat(result.captured()).containsEntry("page_url", "http://localhost:3003/runs");
        assertThat(result.captured()).containsEntry("action", "navigate");
    }

    @Test
    void click_returnsPassedWithSelector() throws Exception {
        TestStep.Send step = step("click", Map.of("action", "click", "selector", "run-trigger-btn"));
        StepResult.Passed result = adapter.execute(step, Map.of());
        assertThat(result.captured()).containsEntry("selector", "run-trigger-btn");
    }

    @Test
    void fill_returnsPassedWithSelectorAndValue() throws Exception {
        TestStep.Send step = step("fill", Map.of("action", "fill", "selector", "amount-input", "value", "100"));
        StepResult.Passed result = adapter.execute(step, Map.of());
        assertThat(result.captured()).containsEntry("value", "100");
    }

    @Test
    void assertText_returnsPassedWithExpectedText() throws Exception {
        TestStep.Send step = step("assert_text",
                Map.of("action", "assert_text", "selector", "transaction-status-badge", "expected_text", "APPROVED"));
        StepResult.Passed result = adapter.execute(step, Map.of());
        assertThat(result.captured()).containsEntry("expected_text", "APPROVED");
    }

    @Test
    void screenshot_returnsPassedWithScreenshotKey() throws Exception {
        TestStep.Send step = step("screenshot", Map.of("action", "screenshot"));
        StepResult.Passed result = adapter.execute(step, Map.of());
        assertThat(result.captured()).containsKey("screenshot_base64");
    }

    @Test
    void unknownAction_throwsIllegalArgument() {
        TestStep.Send step = step("bad", Map.of("action", "hover"));
        assertThatThrownBy(() -> adapter.execute(step, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Playwright action");
    }

    @Test
    void missingActionKey_throwsIllegalArgument() {
        TestStep.Send step = step("click", Map.of("selector", "btn"));
        assertThatThrownBy(() -> adapter.execute(step, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }

    @Test
    void clickMissingSelector_throwsIllegalArgument() {
        TestStep.Send step = step("click", Map.of("action", "click"));
        assertThatThrownBy(() -> adapter.execute(step, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selector");
    }

    private TestStep.Send step(String operation, Map<String, Object> payload) {
        return new TestStep.Send(ChannelType.PLAYWRIGHT, operation, payload, Duration.ofSeconds(10), null);
    }
}
