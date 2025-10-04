package com.ecommerce.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for checkout response.
 * Matches OpenAPI schema for CheckoutResponse.
 */
public record CheckoutResponse(
        @NotBlank
        String orderNumber,

        @NotNull
        String status,

        @NotBlank
        String message
) {
}


