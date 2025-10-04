package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;

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
        String correlationId,

        List<String> details
) {

    public static ErrorResponse of(HttpStatus status, String error, String message, String correlationId) {
        return new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                error,
                message,
                correlationId,
                null
        );
    }

    public static ErrorResponse of(HttpStatus status, String error, String message, String correlationId, List<String> details) {
        return new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                error,
                message,
                correlationId,
                details
        );
    }
}

