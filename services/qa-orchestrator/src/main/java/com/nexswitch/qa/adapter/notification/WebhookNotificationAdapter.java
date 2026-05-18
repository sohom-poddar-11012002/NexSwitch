package com.nexswitch.qa.adapter.notification;

import com.nexswitch.qa.domain.port.outbound.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "qa.notifications.webhook-url")
public class WebhookNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationAdapter.class);

    private final RestClient restClient;
    private final String     webhookUrl;

    public WebhookNotificationAdapter(
            RestClient.Builder builder,
            @Value("${qa.notifications.webhook-url}") String webhookUrl) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void notify(String title, String body) {
        try {
            restClient.post().uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", "*" + title + "*\n" + body))
                .retrieve()
                .toBodilessEntity();
            log.info("qa.notification.sent title={}", title);
        } catch (Exception e) {
            log.warn("qa.notification.failed title={} err={}", title, e.getMessage());
        }
    }
}
