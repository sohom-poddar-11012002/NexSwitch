package com.nexswitch.acquiring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.nexswitch.acquiring", "com.nexswitch.app", "com.nexswitch.adapters"})
public class AcquiringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcquiringServiceApplication.class, args);
    }
}
