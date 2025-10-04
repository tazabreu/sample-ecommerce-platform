package com.ecommerce.order.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Micrometer metrics configuration for order management service.
 * Exposes custom business metrics and configures common tags.
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
}

