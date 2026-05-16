package com.nexswitch.app.config;

import org.springframework.context.annotation.Configuration;

// LEARN: Redis is single-threaded for command execution — atomic ops (SETNX, GETDEL) need no
//        application-level locks. Lettuce (Spring's default driver) uses Netty; a single connection
//        handles all commands via pipelining, unlike JDBC which pools per-thread connections.
/** Shared RedisConfig — populated as infrastructure is wired in Sprint Week 1. */
@Configuration
public class RedisConfig {
}
