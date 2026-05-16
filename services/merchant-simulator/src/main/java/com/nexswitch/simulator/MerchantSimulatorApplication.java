package com.nexswitch.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.nexswitch.simulator", "com.nexswitch.app", "com.nexswitch.adapters"})
public class MerchantSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantSimulatorApplication.class, args);
    }
}
