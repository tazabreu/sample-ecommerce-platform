package com.ecommerce.customer.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import com.ecommerce.shared.event.OrderCreatedEvent;

/**
 * Spring Boot Actuator configuration for customer-facing service.
 * Provides custom health indicators for infrastructure dependencies.
 */
@Configuration
public class ActuatorConfig {

    /**
     * Custom health indicator for Redis connection.
     */
    @Bean
    public HealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return () -> {
            try {
                connectionFactory.getConnection().ping();
                return Health.up().withDetail("redis", "Available").build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("redis", "Unavailable")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * Custom health indicator for Kafka producer.
     */
    @Bean
    public HealthIndicator kafkaHealthIndicator(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        return () -> {
            try {
                // Check if Kafka template is available
                if (kafkaTemplate != null) {
                    return Health.up().withDetail("kafka", "Available").build();
                }
                return Health.down().withDetail("kafka", "Unavailable").build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("kafka", "Unavailable")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }
}

