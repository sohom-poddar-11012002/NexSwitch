package com.nexswitch.qa.adapter.channel;

import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaosTestAdapterTest {

    @Test
    void dockerPause_invokesDockerCommand() throws Exception {
        List<String[]> captured = new ArrayList<>();
        ChaosTestAdapter adapter = new ChaosTestAdapter(args -> { captured.add(args); return 0; });

        TestStep.Send step = send("docker_pause", Map.of("container", "nexswitch-redis-1"));
        StepResult.Passed result = adapter.execute(step, Map.of());

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).containsExactly("docker", "pause", "nexswitch-redis-1");
        assertThat(result.stepId()).isEqualTo("docker_pause");
    }

    @Test
    void dockerUnpause_invokesDockerCommand() throws Exception {
        List<String[]> captured = new ArrayList<>();
        ChaosTestAdapter adapter = new ChaosTestAdapter(args -> { captured.add(args); return 0; });

        TestStep.Send step = send("docker_unpause", Map.of("container", "nexswitch-redis-1"));
        adapter.execute(step, Map.of());

        assertThat(captured.get(0)).containsExactly("docker", "unpause", "nexswitch-redis-1");
    }

    @Test
    void sleepMs_doesNotInvokeExternalCommand() throws Exception {
        List<String[]> captured = new ArrayList<>();
        ChaosTestAdapter adapter = new ChaosTestAdapter(args -> { captured.add(args); return 0; });

        TestStep.Send step = send("sleep_ms", Map.of("duration_ms", "50"));
        StepResult.Passed result = adapter.execute(step, Map.of());

        assertThat(captured).isEmpty();
        assertThat(result.elapsed().toMillis()).isGreaterThanOrEqualTo(50L);
    }

    @Test
    void unknownOperation_throwsIllegalArgument() {
        ChaosTestAdapter adapter = new ChaosTestAdapter(args -> 0);
        TestStep.Send step = send("definitely_not_a_real_op", Map.of());

        assertThatThrownBy(() -> adapter.execute(step, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown chaos operation");
    }

    @Test
    void missingContainerKey_throwsIllegalArgument() {
        ChaosTestAdapter adapter = new ChaosTestAdapter(args -> 0);
        TestStep.Send step = send("docker_pause", Map.of()); // no container key

        assertThatThrownBy(() -> adapter.execute(step, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("container");
    }

    @Test
    void supportsChaosCannelOnly() {
        ChaosTestAdapter adapter = new ChaosTestAdapter(args -> 0);
        assertThat(adapter.supports(ChannelType.CHAOS)).isTrue();
        assertThat(adapter.supports(ChannelType.ISO8583)).isFalse();
        assertThat(adapter.supports(ChannelType.KAFKA_ASSERT)).isFalse();
    }

    private TestStep.Send send(String operation, Map<String, Object> payload) {
        return new TestStep.Send(ChannelType.CHAOS, operation, payload, Duration.ofSeconds(5), null);
    }
}
