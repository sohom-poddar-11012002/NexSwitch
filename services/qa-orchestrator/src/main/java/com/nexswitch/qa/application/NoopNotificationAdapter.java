package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.port.outbound.NotificationPort;

public class NoopNotificationAdapter implements NotificationPort {
    @Override
    public void notify(String title, String body) { }
}
