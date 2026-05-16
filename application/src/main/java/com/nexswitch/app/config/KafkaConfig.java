package com.nexswitch.app.config;

import org.springframework.context.annotation.Configuration;

// LEARN: Kafka producers/consumers are thread-safe — one shared instance per app is the norm.
//        Partitions are the unit of parallelism: consumers in the same group each own a partition,
//        so throughput scales by adding partitions, not consumer threads.
/** Shared KafkaConfig — populated as infrastructure is wired in Sprint Week 1. */
@Configuration
public class KafkaConfig {
}
