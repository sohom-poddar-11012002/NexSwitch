package com.payments.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.payments.simulator", "com.payments.app", "com.payments.adapters"})
public class MerchantSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantSimulatorApplication.class, args);
    }
}
