package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.port.outbound.NotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(NotificationPort.class)
public class NoopNotificationAdapter implements NotificationPort {
    @Override
    public void notify(String title, String body) { }
}
