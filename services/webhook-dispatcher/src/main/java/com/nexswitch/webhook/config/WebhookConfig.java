package com.nexswitch.webhook.config;

import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.webhook.persistence.JpaWebhookDeliveryRepository;
import com.nexswitch.webhook.service.WebhookDeliveryService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

// LEARN: CompositionRoot — all infrastructure beans wired here; service classes stay free of @Autowired.
@Configuration
public class WebhookConfig {

    private final String bootstrapServers;
    private final String dlqTopic;
    private final int    maxAttempts;

    public WebhookConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${webhook.dlq-topic:webhook.dlq}") String dlqTopic,
            @Value("${webhook.max-attempts:5}") int maxAttempts) {
        this.bootstrapServers = bootstrapServers;
        this.dlqTopic         = dlqTopic;
        this.maxAttempts      = maxAttempts;
    }

    @Bean
    public WebhookDeliveryService.HttpSender restClientHttpSender() {
        // LEARN: Explicit connect + read timeout — without this, a non-responding merchant endpoint
        //        holds the @KafkaListener thread indefinitely, eventually exhausting the thread pool.
        //        Spec mandates 5s webhook timeout; we apply it at the HTTP transport layer.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));

        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "NexSwitch-WebhookDispatcher/1.0")
                .build();

        // LEARN: RestClient — Spring Boot 4's replacement for RestTemplate; fluent, synchronous,
        //        returns the HTTP status code without throwing on 4xx/5xx so we can record it.
        return (url, body, headers) -> {
            try {
                return restClient.post()
                        .uri(url)
                        .headers(h -> headers.forEach(h::add))
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
                        .getStatusCode()
                        .value();
            } catch (Exception e) {
                return 503;
            }
        };
    }

    @Bean
    public WebhookDeliveryService.DlqPublisher kafkaDlqPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        return delivery -> kafkaTemplate.send(dlqTopic, delivery.merchantId(), delivery.payload());
    }

    @Bean
    public WebhookDeliveryService webhookDeliveryService(
            MerchantRepository merchantRepository,
            JpaWebhookDeliveryRepository deliveryRepository,
            WebhookDeliveryService.HttpSender httpSender,
            WebhookDeliveryService.DlqPublisher dlqPublisher) {
        return new WebhookDeliveryService(merchantRepository, deliveryRepository, httpSender, maxAttempts, dlqPublisher);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> webhookKafkaListenerContainerFactory(
            KafkaTemplate<String, String> kafkaTemplate) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,           "webhook-dispatcher",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.ISOLATION_LEVEL_CONFIG,    "read_committed",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest"
        );
        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        // LEARN: MANUAL_IMMEDIATE — offset committed synchronously right after ack.acknowledge()
        //        ensuring no message is lost even if the JVM crashes between delivery and offset commit.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // LEARN: DefaultErrorHandler — retries 3× at 1s intervals before routing to DLQ.
        //        Without this, a poison-pill message causes infinite redelivery and blocks all webhook delivery.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (r, e) -> new TopicPartition(dlqTopic, -1));
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3)));
        return factory;
    }
}
