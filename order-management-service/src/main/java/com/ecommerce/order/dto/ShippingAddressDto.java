package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for shipping address.
 * Matches OpenAPI schema for ShippingAddress.
 */
public record ShippingAddressDto(
        @NotBlank
        String street,

        @NotBlank
        String city,

        @NotBlank
        String state,

        @NotBlank
        String postalCode,

        @NotBlank
        String country
) {
}


