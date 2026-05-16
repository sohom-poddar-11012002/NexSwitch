package com.nexswitch.chargeback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// LEARN: @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan.
//        scanBasePackages lists every module JAR so Spring finds beans across sibling modules.
//        Auto-configuration reads META-INF/spring/factories — Spring Boot wires 200+ defaults
//        (DataSource, Kafka, Redis) based purely on what JARs are on the classpath.
@SpringBootApplication(scanBasePackages = {"com.nexswitch.chargeback", "com.nexswitch.app", "com.nexswitch.adapters"})
public class ChargebackServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChargebackServiceApplication.class, args);
    }
}
