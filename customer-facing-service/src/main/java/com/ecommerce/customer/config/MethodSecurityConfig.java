package com.ecommerce.customer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method-level security configuration.
 * Enables @PreAuthorize, @PostAuthorize, @Secured annotations.
 * 
 * <p>Only active in non-test profiles to allow contract tests to run without authentication.</p>
 */
@Configuration
@Profile("!test")
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {
    // Method security enabled only when not in test profile
}