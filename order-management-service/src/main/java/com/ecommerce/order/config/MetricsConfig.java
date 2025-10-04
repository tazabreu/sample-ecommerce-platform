package com.ecommerce.order.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Micrometer metrics configuration for order management service.
 * Exposes custom business metrics and configures common tags.
 *
 * <p>Business metrics exposed:</p>
 * <ul>
 *   <li>orders_created_total - Total number of orders created</li>
 *   <li>payments_success_total - Total number of successful payments</li>
 *   <li>payments_failed_total - Total number of failed payments</li>
 *   <li>order_processing_duration - Timer for order processing duration</li>
 *   <li>payment_processing_duration - Timer for payment processing duration</li>
 * </ul>
 */
@Configuration
public class MetricsConfig {

    /**
     * Customizes meter registry with common tags for all metrics.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(Environment environment) {
        return registry -> registry.config().commonTags(
                Tags.of(
                        Tag.of("application", "order-management-service"),
                        Tag.of("environment", environment.getProperty("ENVIRONMENT", "local"))
                )
        );
    }

    /**
     * Counter for total orders created.
     */
    @Bean
    public Counter ordersCreatedCounter(MeterRegistry registry) {
        return Counter.builder("orders.created.total")
                .description("Total number of orders created")
                .register(registry);
    }

    /**
     * Counter for successful payments.
     */
    @Bean
    public Counter paymentsSuccessCounter(MeterRegistry registry) {
        return Counter.builder("payments.success.total")
                .description("Total number of successful payments")
                .register(registry);
    }

    /**
     * Counter for failed payments.
     */
    @Bean
    public Counter paymentsFailedCounter(MeterRegistry registry) {
        return Counter.builder("payments.failed.total")
                .description("Total number of failed payments")
                .register(registry);
    }

    /**
     * Timer for order processing duration.
     */
    @Bean
    public Timer orderProcessingTimer(MeterRegistry registry) {
        return Timer.builder("order.processing.duration")
                .description("Duration of order processing")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * Timer for payment processing duration.
     */
    @Bean
    public Timer paymentProcessingTimer(MeterRegistry registry) {
        return Timer.builder("payment.processing.duration")
                .description("Duration of payment processing")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}

