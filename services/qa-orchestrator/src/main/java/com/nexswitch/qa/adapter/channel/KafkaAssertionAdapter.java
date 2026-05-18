package com.nexswitch.qa.adapter.channel;

import com.jayway.jsonpath.JsonPath;
import com.nexswitch.qa.domain.model.ChannelType;
import com.nexswitch.qa.domain.model.StepResult;
import com.nexswitch.qa.domain.model.TestStep;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

// LEARN: Manual KafkaConsumer (not @KafkaListener) — created per assertion, subscribed,
//        seeked to end, then polled until the expected event arrives or timeout expires.
//        Using a unique group ID ensures the consumer sees all messages from the current offset
//        rather than resuming from a shared committed offset.
@Component
public class KafkaAssertionAdapter implements TestChannelPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaAssertionAdapter.class);

    private final String bootstrapServers;

    public KafkaAssertionAdapter(
            @org.springframework.beans.factory.annotation.Value(
                "${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public boolean supports(ChannelType channelType) {
        return channelType == ChannelType.KAFKA_ASSERT;
    }

    // Payload keys: topic, jsonpath, expected_value
    // operation: "assert_kafka_event"
    @Override
    public StepResult.Passed execute(TestStep.Send step, Map<String, Object> context) throws Exception {
        String topic         = require(step.payload(), "topic");
        String jsonpath      = require(step.payload(), "jsonpath");
        String expectedValue = require(step.payload(), "expected_value");
        Instant start        = Instant.now();

        // LEARN: createConsumer() factory — production returns new KafkaConsumer(consumerProps());
        //        tests override to inject a mock without a real broker.
        // LEARN: seek-to-end pattern — poll once to trigger partition assignment, then
        //        seekToEnd() so we only see messages produced after this assertion started.
        //        Prevents flaky tests that accidentally match stale events from a prior run.
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofMillis(200)); // force partition assignment
            consumer.seekToEnd(consumer.assignment());

            long deadlineMs = System.currentTimeMillis() + step.timeout().toMillis();
            while (System.currentTimeMillis() < deadlineMs) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (var record : records) {
                    try {
                        String actual = JsonPath.read(record.value(), jsonpath).toString();
                        if (expectedValue.equals(actual)) {
                            log.info("qa.kafka.event_found topic={} jsonpath={} offset={}", topic, jsonpath, record.offset());
                            return new StepResult.Passed(step.operation(),
                                    Duration.between(start, Instant.now()),
                                    Map.of("matched_value", actual, "offset", record.offset(),
                                           "partition", record.partition()));
                        }
                    } catch (Exception ignored) { /* record doesn't match jsonpath shape */ }
                }
            }
        }
        throw new AssertionError(
                "Kafka event not found: topic=" + topic + " jsonpath=" + jsonpath
                + " expected=" + expectedValue + " within " + step.timeout());
    }

    // LEARN: Protected factory method — production creates a real KafkaConsumer;
    //        tests subclass and return a mock, avoiding a real Kafka broker in unit tests.
    protected KafkaConsumer<String, String> createConsumer() {
        return new KafkaConsumer<>(consumerProps());
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "qa-assert-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        return props;
    }

    private String require(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) throw new IllegalArgumentException("KafkaAssertionAdapter: missing payload key '" + key + "'");
        return v.toString();
    }
}
