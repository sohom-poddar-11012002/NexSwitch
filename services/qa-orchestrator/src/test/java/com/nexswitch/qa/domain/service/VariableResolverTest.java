package com.nexswitch.qa.domain.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VariableResolverTest {

    private final VariableResolver resolver = new VariableResolver();

    @Test
    void resolves_user_defined_variable() {
        String result = resolver.resolve("Hello {{pan}}", Map.of("pan", "4539148803436467"));
        assertThat(result).isEqualTo("Hello 4539148803436467");
    }

    @Test
    void resolves_stan_as_zero_padded_counter() {
        String first  = resolver.resolve("{{$stan}}", Map.of());
        String second = resolver.resolve("{{$stan}}", Map.of());
        assertThat(first).matches("\\d{6}");
        assertThat(Integer.parseInt(second)).isEqualTo(Integer.parseInt(first) + 1);
    }

    @Test
    void resolves_uuid_as_uuid_format() {
        String result = resolver.resolve("{{$uuid}}", Map.of());
        assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void resolves_now_as_epoch_millis() {
        long before = System.currentTimeMillis();
        String result = resolver.resolve("{{$now}}", Map.of());
        long after = System.currentTimeMillis();
        long value = Long.parseLong(result);
        assertThat(value).isBetween(before, after);
    }

    @Test
    void leaves_unknown_variable_unresolved() {
        String result = resolver.resolve("{{missing}}", Map.of());
        assertThat(result).isEqualTo("{{missing}}");
    }

    @Test
    void resolves_multiple_variables_in_one_template() {
        String result = resolver.resolve("{{pan}}/{{currency}}", Map.of("pan", "4111111111111111", "currency", "356"));
        assertThat(result).isEqualTo("4111111111111111/356");
    }

    @Test
    void null_template_returns_null() {
        assertThat(resolver.resolve(null, Map.of())).isNull();
    }

    @Test
    void resolves_all_entries_in_map() {
        Map<String, Object> payload = Map.of("pan", "{{pan}}", "amount", "{{amount}}");
        Map<String, Object> ctx     = Map.of("pan", "4539148803436467", "amount", "000000600000");
        Map<String, Object> result  = resolver.resolveAll(payload, ctx);
        assertThat(result).containsEntry("pan", "4539148803436467")
                          .containsEntry("amount", "000000600000");
    }
}
