package com.payments.domain.port.outbound;

import java.util.Map;

// LEARN: AdapterPort — email/SMS template resolution is adapter concern; domain passes templateName
public interface NotificationPort {
    void send(String recipient, String subject, String templateName, Map<String, Object> templateVars);
}
