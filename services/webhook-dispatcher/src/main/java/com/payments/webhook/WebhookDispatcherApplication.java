package com.payments.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.payments.webhook", "com.payments.app", "com.payments.adapters"})
public class WebhookDispatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookDispatcherApplication.class, args);
    }
}
