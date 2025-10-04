package com.ecommerce.customer.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j configuration for customer-facing service.
 * Configures circuit breakers, retries, and timeouts for resilience patterns.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Circuit breaker for Kafka publishing.
     * Opens after 50% failure rate in 10 calls, waits 30s before half-open.
     */
    @Bean
    public CircuitBreaker kafkaPublisherCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return registry.circuitBreaker("kafkaPublisher", config);
    }

    /**
     * Retry policy for Kafka publishing.
     * 3 attempts with exponential backoff (1s, 2s, 4s).
     */
    @Bean
    public Retry kafkaPublisherRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .build();

        return registry.retry("kafkaPublisher", config);
    }

    /**
     * Time limiter for catalog queries.
     * Timeout: 500ms for fast catalog responses.
     */
    @Bean
    public TimeLimiter catalogQueryTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return registry.timeLimiter("catalogQuery", config);
    }
}

