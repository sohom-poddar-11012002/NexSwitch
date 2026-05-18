package com.nexswitch.qa.domain.port.outbound;

public interface NotificationPort {
    void notify(String title, String body);
}
