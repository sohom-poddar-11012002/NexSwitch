package com.nexswitch.chargeback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.nexswitch.chargeback", "com.nexswitch.app", "com.nexswitch.adapters"})
public class ChargebackServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChargebackServiceApplication.class, args);
    }
}
