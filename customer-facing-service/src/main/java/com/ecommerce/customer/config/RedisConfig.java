package com.ecommerce.customer.config;

import com.ecommerce.customer.model.Cart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis caching.
 * Used for cart session management with TTL-based expiration.
 */
@Configuration
public class RedisConfig {

    /**
     * Configure RedisTemplate for Cart objects.
     * Uses JSON serialization for cart data.
     *
     * @param connectionFactory the Redis connection factory
     * @return configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Cart> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Configure ObjectMapper for proper date/time handling
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = 
                new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}


