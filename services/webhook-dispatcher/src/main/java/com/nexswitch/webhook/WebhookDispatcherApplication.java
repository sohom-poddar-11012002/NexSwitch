package com.nexswitch.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.nexswitch.webhook", "com.nexswitch.app", "com.nexswitch.adapters"})
public class WebhookDispatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookDispatcherApplication.class, args);
    }
}
