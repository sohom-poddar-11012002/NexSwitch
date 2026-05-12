package com.payments.domain.port.outbound;

import java.util.Map;

public interface NotificationPort {
    void send(String recipient, String subject, String templateName, Map<String, Object> templateVars);
}
