package com.nexswitch.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.nexswitch.reconciliation", "com.nexswitch.app", "com.nexswitch.adapters"})
public class ReconciliationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationServiceApplication.class, args);
    }
}
