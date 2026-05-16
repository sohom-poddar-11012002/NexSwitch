package com.nexswitch.payswitch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.nexswitch.payswitch", "com.nexswitch.app", "com.nexswitch.adapters"})
public class PaymentSwitchApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentSwitchApplication.class, args);
    }
}
