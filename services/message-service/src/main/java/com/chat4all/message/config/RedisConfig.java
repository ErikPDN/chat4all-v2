package com.chat4all.message.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Configuration for Reactive Message Service
 * 
 * Configures ReactiveRedisTemplate for idempotency checks.
 * Uses Lettuce driver for non-blocking Redis operations.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a ReactiveRedisTemplate bean with String serialization.
     * 
     * Used by IdempotencyService for checking message deduplication.
     * 
     * @param connectionFactory Reactive Redis connection factory (auto-configured by Spring Boot)
     * @return ReactiveRedisTemplate for String key-value operations
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        StringRedisSerializer serializer = new StringRedisSerializer();
        
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
            .<String, String>newSerializationContext()
            .key(serializer)
            .value(serializer)
            .hashKey(serializer)
            .hashValue(serializer)
            .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
