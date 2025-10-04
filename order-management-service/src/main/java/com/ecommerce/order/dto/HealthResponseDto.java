package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * DTO for health check responses.
 * Matches OpenAPI schema for HealthResponse.
 */
public record HealthResponseDto(
        @NotNull
        String status,

        Map<String, ComponentHealth> components
) {
    public record ComponentHealth(
            String status,
            Map<String, Object> details
    ) {
    }
}


