package com.nexswitch.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.nexswitch.settlement", "com.nexswitch.app", "com.nexswitch.adapters"})
@EnableScheduling
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
