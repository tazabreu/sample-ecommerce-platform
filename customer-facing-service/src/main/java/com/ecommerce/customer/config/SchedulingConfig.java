package com.ecommerce.customer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for enabling scheduled tasks.
 * Enables @Scheduled annotation support for background jobs like outbox publisher.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
