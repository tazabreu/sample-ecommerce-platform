package com.ecommerce.customer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logging configuration for customer-facing service.
 * Sets up correlation IDs in MDC for request tracing.
 */
@Configuration
public class LoggingConfig {

    /**
     * Filter to add correlation ID to MDC for each request.
     * Generates UUID if X-Correlation-ID header is not present.
     */
    @Bean
    public OncePerRequestFilter correlationIdFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                          HttpServletResponse response,
                                          FilterChain filterChain) throws ServletException, IOException {
                try {
                    // Get correlation ID from header or generate new one
                    String correlationId = request.getHeader("X-Correlation-ID");
                    if (correlationId == null || correlationId.isBlank()) {
                        correlationId = UUID.randomUUID().toString();
                    }

                    // Add to MDC for logging
                    MDC.put("correlationId", correlationId);

                    // Add to response header
                    response.setHeader("X-Correlation-ID", correlationId);

                    filterChain.doFilter(request, response);
                } finally {
                    // Clean up MDC
                    MDC.clear();
                }
            }
        };
    }
}

