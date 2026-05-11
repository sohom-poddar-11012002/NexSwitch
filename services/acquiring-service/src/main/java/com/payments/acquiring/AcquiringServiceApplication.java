package com.payments.acquiring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.payments.acquiring", "com.payments.app", "com.payments.adapters"})
public class AcquiringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcquiringServiceApplication.class, args);
    }
}
