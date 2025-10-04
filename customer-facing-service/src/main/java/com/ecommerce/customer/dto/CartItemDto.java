package com.ecommerce.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for CartItem responses.
 * Matches OpenAPI schema for CartItem entity.
 */
public record CartItemDto(
        @NotNull
        UUID id,

        @NotNull
        UUID productId,

        @NotNull
        String productSku,

        @NotNull
        String productName,

        @NotNull
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @NotNull
        @DecimalMin(value = "0.01", message = "Price snapshot must be at least 0.01")
        BigDecimal priceSnapshot,

        @NotNull
        @DecimalMin(value = "0.01", message = "Subtotal must be at least 0.01")
        BigDecimal subtotal
) {
}


