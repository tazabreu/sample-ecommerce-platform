package com.ecommerce.customer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO for adding an item to the cart.
 * Matches OpenAPI schema for AddCartItemRequest.
 */
public record AddCartItemRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {
}


