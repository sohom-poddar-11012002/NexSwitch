package com.payments.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.payments.settlement", "com.payments.app", "com.payments.adapters"})
@EnableScheduling
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
