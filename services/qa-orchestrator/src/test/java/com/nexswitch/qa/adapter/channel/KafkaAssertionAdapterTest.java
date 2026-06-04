package com.nexswitch.qa.adapter.channel;

import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// LEARN: KafkaAssertionAdapter uses a real KafkaConsumer internally; tests mock KafkaConsumer
//        via a subclass that overrides createConsumer() — avoids a real broker while verifying
//        the earliest-offset + JSONPath matching logic.
@ExtendWith(MockitoExtension.class)
class KafkaAssertionAdapterTest {

    @Mock KafkaConsumer<String, String> consumer;

    private KafkaAssertionAdapterTestable adapter;

    @BeforeEach
    void setUp() {
        adapter = new KafkaAssertionAdapterTestable(consumer);
    }

    @Test
    void eventFound_returnsPassedWithMatchedValue() throws Exception {
        TopicPartition tp = new TopicPartition("authorization-events", 0);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "authorization-events", 0, 10L, "key",
                "{\"eventType\":\"AUTHORIZATION_APPROVED\",\"transactionId\":\"tx-1\"}");
        @SuppressWarnings("deprecation")
        ConsumerRecords<String, String> records = new ConsumerRecords<>(Map.of(tp, List.of(record)));

        when(consumer.poll(Duration.ofMillis(500))).thenReturn(records);

        TestStep.Send step = new TestStep.Send(ChannelType.KAFKA_ASSERT, "assert_kafka_event",
                Map.of("topic", "authorization-events",
                       "jsonpath", "$.eventType",
                       "expected_value", "AUTHORIZATION_APPROVED"),
                Duration.ofSeconds(5), "kafka_event");

        StepResult.Passed result = adapter.execute(step, Map.of());

        assertThat(result.captured()).containsEntry("matched_value", "AUTHORIZATION_APPROVED");
        assertThat(result.captured()).containsKey("offset");
    }

    @Test
    void eventNotFound_throwsAssertionError() {
        lenient().when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());

        TestStep.Send step = new TestStep.Send(ChannelType.KAFKA_ASSERT, "assert_kafka_event",
                Map.of("topic", "authorization-events",
                       "jsonpath", "$.eventType",
                       "expected_value", "AUTHORIZATION_APPROVED"),
                Duration.ofMillis(100), "kafka_event");

        assertThatThrownBy(() -> adapter.execute(step, Map.of()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Kafka event not found");
    }

    @Test
    void missingTopicPayloadKey_throwsIllegalArgument() {
        TestStep.Send step = new TestStep.Send(ChannelType.KAFKA_ASSERT, "assert_kafka_event",
                Map.of("jsonpath", "$.eventType", "expected_value", "X"),
                Duration.ofSeconds(5), null);

        assertThatThrownBy(() -> adapter.execute(step, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void supportsKafkaAssertOnly() {
        KafkaAssertionAdapter real = new KafkaAssertionAdapter("localhost:9092");
        assertThat(real.supports(ChannelType.KAFKA_ASSERT)).isTrue();
        assertThat(real.supports(ChannelType.ISO8583)).isFalse();
        assertThat(real.supports(ChannelType.CHAOS)).isFalse();
    }

    // Testable subclass that injects a mocked consumer instead of creating a real one
    static class KafkaAssertionAdapterTestable extends KafkaAssertionAdapter {
        private final KafkaConsumer<String, String> mockConsumer;

        KafkaAssertionAdapterTestable(KafkaConsumer<String, String> mockConsumer) {
            super("localhost:9092");
            this.mockConsumer = mockConsumer;
        }

        @Override
        protected KafkaConsumer<String, String> createConsumer() {
            return mockConsumer;
        }
    }
}
