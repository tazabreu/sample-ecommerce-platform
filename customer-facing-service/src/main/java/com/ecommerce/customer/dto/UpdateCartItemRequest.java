package com.ecommerce.customer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating a cart item quantity.
 * Matches OpenAPI schema for UpdateCartItemRequest.
 */
public record UpdateCartItemRequest(
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {
}


