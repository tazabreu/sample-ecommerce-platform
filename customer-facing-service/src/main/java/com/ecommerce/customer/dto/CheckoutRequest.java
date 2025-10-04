package com.ecommerce.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for checkout request.
 * Matches OpenAPI schema for CheckoutRequest.
 */
public record CheckoutRequest(
        @NotBlank(message = "Session ID is required")
        String sessionId,

        @NotNull(message = "Customer information is required")
        @Valid
        CustomerInfoDto customerInfo
) {
}


