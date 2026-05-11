package com.payments.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.payments.reconciliation", "com.payments.app", "com.payments.adapters"})
public class ReconciliationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationServiceApplication.class, args);
    }
}
