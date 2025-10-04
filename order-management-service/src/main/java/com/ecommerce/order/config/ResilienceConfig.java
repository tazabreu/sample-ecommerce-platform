package com.ecommerce.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Resilience4j configuration for order management service.
 * Configures circuit breakers, retries, and timeouts for payment service calls.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Circuit breaker for payment service.
     * Opens after 50% failure rate in 10 calls, waits 30s before half-open.
     * Also tracks slow calls (>5s) with 60% threshold.
     */
    @Bean
    public CircuitBreaker paymentServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .slowCallRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return registry.circuitBreaker("paymentService", config);
    }

    /**
     * Retry policy for payment service.
     * 3 attempts with exponential backoff (1s, 2s, 4s).
     * Only retries on IOException and TimeoutException.
     */
    @Bean
    public Retry paymentServiceRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2.0))
                .retryExceptions(IOException.class, TimeoutException.class)
                .build();

        return registry.retry("paymentService", config);
    }

    /**
     * Time limiter for payment service.
     * Timeout: 5s for payment processing.
     */
    @Bean
    public TimeLimiter paymentServiceTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        return registry.timeLimiter("paymentService", config);
    }
}

