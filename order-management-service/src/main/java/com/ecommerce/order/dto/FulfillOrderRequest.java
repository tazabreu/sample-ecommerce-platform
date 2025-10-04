package com.ecommerce.order.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for fulfilling an order.
 * Matches OpenAPI schema for FulfillOrderRequest.
 */
public record FulfillOrderRequest(
        @Size(max = 100, message = "Tracking number must not exceed 100 characters")
        String trackingNumber,

        @Size(max = 50, message = "Carrier name must not exceed 50 characters")
        String carrier,

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes
) {
}


