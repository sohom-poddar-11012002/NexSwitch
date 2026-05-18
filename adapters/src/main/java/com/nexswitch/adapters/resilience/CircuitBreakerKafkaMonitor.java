package com.nexswitch.adapters.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

// LEARN: Backpressure — when the network circuit opens, processing new Kafka events would
//        queue work that will immediately fail (no upstream). Pausing the consumer lets the
//        Kafka broker buffer messages; the consumer resumes when the circuit closes/half-opens,
//        avoiding unbounded in-memory queuing on the JVM side.
@Component
public class CircuitBreakerKafkaMonitor {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerKafkaMonitor.class);

    private final CircuitBreakerRegistry      registry;
    private final KafkaListenerEndpointRegistry kafkaRegistry;

    public CircuitBreakerKafkaMonitor(
            CircuitBreakerRegistry registry,
            KafkaListenerEndpointRegistry kafkaRegistry) {
        this.registry      = registry;
        this.kafkaRegistry = kafkaRegistry;
    }

    @PostConstruct
    void subscribeToNetworkCircuit() {
        // Watch only the "network" circuit — HSM failures don't affect Kafka consumption
        registry.find("network").ifPresent(cb ->
            cb.getEventPublisher().onStateTransition(this::onStateTransition)
        );
    }

    private void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreaker.State to = event.getStateTransition().getToState();
        log.info("circuit.state_change name=network to={}", to);

        // LEARN: pause() is non-blocking — the container finishes the current poll cycle then
        //        stops fetching. resume() re-subscribes with the last committed offset so no
        //        messages are skipped. Both are thread-safe on MessageListenerContainer.
        switch (to) {
            case OPEN -> pauseAllContainers();
            case CLOSED, HALF_OPEN -> resumeAllContainers();
            default -> { /* DISABLED, FORCED_OPEN — leave containers unchanged */ }
        }
    }

    private void pauseAllContainers() {
        for (MessageListenerContainer container : kafkaRegistry.getAllListenerContainers()) {
            if (container.isRunning()) {
                container.pause();
                log.warn("kafka.consumer.paused id={}", container.getListenerId());
            }
        }
    }

    private void resumeAllContainers() {
        for (MessageListenerContainer container : kafkaRegistry.getAllListenerContainers()) {
            if (container.isRunning()) {
                container.resume();
                log.info("kafka.consumer.resumed id={}", container.getListenerId());
            }
        }
    }
}
