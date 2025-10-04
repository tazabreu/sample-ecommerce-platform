package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for Order responses.
 * Matches OpenAPI schema for Order entity.
 */
public record OrderDto(
        @NotNull
        UUID id,

        @NotNull
        String orderNumber,

        @NotNull
        CustomerInfoDto customerInfo,

        @NotNull
        List<OrderItemDto> items,

        @NotNull
        BigDecimal subtotal,

        @NotNull
        String status,

        String paymentStatus,

        @NotNull
        LocalDateTime createdAt,

        @NotNull
        LocalDateTime updatedAt,

        LocalDateTime completedAt
) {
}


