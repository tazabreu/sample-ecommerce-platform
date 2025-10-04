package com.ecommerce.customer.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for Cart responses.
 * Matches OpenAPI schema for Cart entity.
 */
public record CartDto(
        @NotNull
        UUID id,

        @NotNull
        String sessionId,

        @NotNull
        List<CartItemDto> items,

        @NotNull
        BigDecimal subtotal,

        @NotNull
        LocalDateTime createdAt,

        @NotNull
        LocalDateTime updatedAt,

        @NotNull
        LocalDateTime expiresAt
) {
}


