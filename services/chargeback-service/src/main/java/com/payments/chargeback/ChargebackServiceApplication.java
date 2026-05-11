package com.payments.chargeback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.payments.chargeback", "com.payments.app", "com.payments.adapters"})
public class ChargebackServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChargebackServiceApplication.class, args);
    }
}
