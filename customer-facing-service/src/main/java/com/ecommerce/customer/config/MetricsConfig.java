package com.ecommerce.customer.config;

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
 * Micrometer metrics configuration for customer-facing service.
 * Exposes custom business metrics and configures common tags.
 *
 * <p>Business metrics exposed:</p>
 * <ul>
 *   <li>checkout_attempts_total - Total number of checkout attempts</li>
 *   <li>checkout_success_total - Total number of successful checkouts</li>
 *   <li>checkout_failures_total - Total number of failed checkouts</li>
 *   <li>cart_items_added_total - Total number of items added to carts</li>
 *   <li>checkout_duration - Timer for checkout processing duration</li>
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
                        Tag.of("application", "customer-facing-service"),
                        Tag.of("environment", environment.getProperty("ENVIRONMENT", "local"))
                )
        );
    }

    /**
     * Counter for total checkout attempts.
     */
    @Bean
    public Counter checkoutAttemptsCounter(MeterRegistry registry) {
        return Counter.builder("checkout.attempts.total")
                .description("Total number of checkout attempts")
                .register(registry);
    }

    /**
     * Counter for successful checkouts.
     */
    @Bean
    public Counter checkoutSuccessCounter(MeterRegistry registry) {
        return Counter.builder("checkout.success.total")
                .description("Total number of successful checkouts")
                .register(registry);
    }

    /**
     * Counter for failed checkouts.
     */
    @Bean
    public Counter checkoutFailuresCounter(MeterRegistry registry) {
        return Counter.builder("checkout.failures.total")
                .description("Total number of failed checkouts")
                .register(registry);
    }

    /**
     * Counter for items added to cart.
     */
    @Bean
    public Counter cartItemsAddedCounter(MeterRegistry registry) {
        return Counter.builder("cart.items.added.total")
                .description("Total number of items added to carts")
                .register(registry);
    }

    /**
     * Timer for checkout processing duration.
     */
    @Bean
    public Timer checkoutDurationTimer(MeterRegistry registry) {
        return Timer.builder("checkout.duration")
                .description("Duration of checkout processing")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}

