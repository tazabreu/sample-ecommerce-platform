package com.ecommerce.order.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring Boot Actuator configuration for order management service.
 * Provides custom health indicators for infrastructure dependencies.
 */
@Configuration
public class ActuatorConfig {

    /**
     * Custom health indicator for Kafka consumer/producer.
     */
    @Bean
    public HealthIndicator kafkaHealthIndicator(KafkaTemplate<String, ?> kafkaTemplate) {
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
