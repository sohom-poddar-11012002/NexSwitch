package com.nexswitch.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// LEARN: Redis is single-threaded for command execution — atomic ops (SETNX, GETDEL) need no
//        application-level locks. Lettuce (Spring's default driver) uses Netty; a single connection
//        handles all commands via pipelining, unlike JDBC which pools per-thread connections.
// LEARN: RedisTemplate<String,String> with StringRedisSerializer avoids Java serialization —
//        human-readable keys/values in Redis CLI and no ClassCastException on schema change.
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        return template;
    }
}
