package com.nexswitch.qa.domain.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionEvaluatorTest {

    private final AssertionEvaluator evaluator = new AssertionEvaluator();

    @Test
    void spel_passes_when_true() {
        AssertionEvaluator.EvaluationResult result = evaluator.evaluate("field39 == '00'",
                Map.of("field39", "00"));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void spel_fails_when_false() {
        AssertionEvaluator.EvaluationResult result = evaluator.evaluate("field39 == '00'",
                Map.of("field39", "51"));
        assertThat(result.passed()).isFalse();
        assertThat(result.actual()).isEqualTo("false");  // SpEL evaluated to false; actual = stringified result
    }

    @Test
    void spel_handles_null_gracefully() {
        AssertionEvaluator.EvaluationResult result = evaluator.evaluate("field39 == '00'", Map.of());
        assertThat(result.passed()).isFalse();
    }

    @Test
    void jsonpath_passes_on_matching_value() {
        String json = "{\"field39\":\"00\",\"approval\":\"ABC123\"}";
        AssertionEvaluator.EvaluationResult result = evaluator.evaluate(
                "$.auth_response.field39 == '00'",
                Map.of("auth_response", json));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void jsonpath_fails_on_wrong_value() {
        String json = "{\"field39\":\"51\"}";
        AssertionEvaluator.EvaluationResult result = evaluator.evaluate(
                "$.auth_response.field39 == '00'",
                Map.of("auth_response", json));
        assertThat(result.passed()).isFalse();
        assertThat(result.actual()).isEqualTo("51");
    }

    @Test
    void jsonpath_fails_when_context_key_missing() {
        AssertionEvaluator.EvaluationResult result = evaluator.evaluate(
                "$.auth_response.field39 == '00'", Map.of());
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("auth_response");
    }

    @Test
    void empty_expression_fails_with_message() {
        AssertionEvaluator.EvaluationResult result = evaluator.evaluate("", Map.of());
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("empty");
    }

    @Test
    void spel_supports_numeric_comparison() {
        AssertionEvaluator.EvaluationResult result = evaluator.evaluate("amount > 0",
                Map.of("amount", 600000));
        assertThat(result.passed()).isTrue();
    }
}
