package com.payments.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.payments.mock", "com.payments.app", "com.payments.adapters"})
public class MockUpstreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockUpstreamApplication.class, args);
    }
}
