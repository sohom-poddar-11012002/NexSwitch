package com.payments.payswitch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.payments.payswitch", "com.payments.app", "com.payments.adapters"})
public class PaymentSwitchApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentSwitchApplication.class, args);
    }
}
