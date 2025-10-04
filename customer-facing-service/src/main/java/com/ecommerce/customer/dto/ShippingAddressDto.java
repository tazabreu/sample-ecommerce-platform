package com.ecommerce.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for shipping address.
 * Matches OpenAPI schema for ShippingAddress.
 */
public record ShippingAddressDto(
        @NotBlank(message = "Street address is required")
        @Size(min = 1, max = 200, message = "Street address must be between 1 and 200 characters")
        String street,

        @NotBlank(message = "City is required")
        @Size(min = 1, max = 100, message = "City must be between 1 and 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(min = 2, max = 100, message = "State must be between 2 and 100 characters")
        String state,

        @NotBlank(message = "Postal code is required")
        @Size(min = 1, max = 20, message = "Postal code must be between 1 and 20 characters")
        String postalCode,

        @NotBlank(message = "Country is required")
        @Size(min = 2, max = 100, message = "Country must be between 2 and 100 characters")
        String country
) {
}


