package com.ecommerce.customer.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for error responses.
 * Matches OpenAPI schema for ErrorResponse.
 */
public record ErrorResponse(
        @NotNull
        LocalDateTime timestamp,

        @NotNull
        Integer status,

        @NotNull
        String error,

        @NotNull
        String message,

        @NotNull
        String path,

        List<String> details
) {
}


