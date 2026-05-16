package com.nexswitch.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.nexswitch.mock", "com.nexswitch.app", "com.nexswitch.adapters"})
public class MockUpstreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockUpstreamApplication.class, args);
    }
}
