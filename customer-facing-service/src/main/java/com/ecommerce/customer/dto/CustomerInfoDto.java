package com.ecommerce.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for customer information.
 * Matches OpenAPI schema for CustomerInfo.
 */
public record CustomerInfoDto(
        @NotBlank(message = "Customer name is required")
        @Size(min = 1, max = 200, message = "Customer name must be between 1 and 200 characters")
        String name,

        @NotBlank(message = "Customer email is required")
        @Email(message = "Invalid email format")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotBlank(message = "Customer phone is required")
        @Pattern(regexp = "^\\+?[0-9]{10,20}$", message = "Phone must be 10-20 digits, optionally starting with +")
        @Size(max = 20, message = "Phone must not exceed 20 characters")
        String phone,

        @NotNull(message = "Shipping address is required")
        @Valid
        ShippingAddressDto shippingAddress
) {
}


