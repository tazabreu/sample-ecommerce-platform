package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for customer information.
 * Matches OpenAPI schema for CustomerInfo.
 */
public record CustomerInfoDto(
        @NotBlank
        String name,

        @NotBlank
        @Email
        String email,

        @NotBlank
        String phone,

        @NotNull
        @Valid
        ShippingAddressDto shippingAddress
) {
}


