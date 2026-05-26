package com.nexswitch.adapters.outbound.notification;

import com.nexswitch.domain.port.outbound.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

// LEARN: StubAdapter — logs the notification instead of sending email; swap for
//        ThymeleafEmailNotificationAdapter when SMTP is wired in a later sprint.
@Component
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void send(String recipient, String subject, String templateName, Map<String, Object> templateVars) {
        log.info("notification.stub recipient={} subject={} template={} vars={}",
                recipient, subject, templateName, templateVars);
    }
}
