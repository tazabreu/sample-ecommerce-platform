package com.ecommerce.customer.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to ensure every request has a correlation ID for distributed tracing.
 * The correlation ID is extracted from the X-Correlation-ID header or generated if not present.
 * The ID is added to MDC for structured logging and propagated to downstream services.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Extract or generate correlation ID
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            // Add to MDC for logging
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // Add to response header for client tracking
            response.addHeader(CORRELATION_ID_HEADER, correlationId);

            // Continue filter chain
            filterChain.doFilter(request, response);
        } finally {
            // Always clean up MDC to prevent memory leaks in thread pools
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
