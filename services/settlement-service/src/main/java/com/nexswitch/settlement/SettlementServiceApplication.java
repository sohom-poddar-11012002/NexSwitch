package com.nexswitch.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// LEARN: @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan.
//        scanBasePackages lists every module JAR so Spring finds beans across sibling modules.
//        Auto-configuration reads META-INF/spring/factories — Spring Boot wires 200+ defaults
//        (DataSource, Kafka, Redis) based purely on what JARs are on the classpath.
@SpringBootApplication(scanBasePackages = {"com.nexswitch.settlement", "com.nexswitch.app", "com.nexswitch.adapters"})
@EnableScheduling
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
