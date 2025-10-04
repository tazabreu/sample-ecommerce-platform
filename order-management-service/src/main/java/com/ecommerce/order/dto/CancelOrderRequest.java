package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for cancelling an order.
 * Matches OpenAPI schema for CancelOrderRequest.
 */
public record CancelOrderRequest(
        @NotBlank(message = "Cancellation reason is required")
        @Size(min = 1, max = 500, message = "Reason must be between 1 and 500 characters")
        String reason
) {
}


