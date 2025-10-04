package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Customer information embedded in OrderCreatedEvent.
 * Contains customer contact details and shipping address.
 */
public record CustomerEvent(
        @NotBlank
        @JsonProperty("name")
        String name,

        @NotBlank
        @Email
        @JsonProperty("email")
        String email,

        @NotBlank
        @JsonProperty("phone")
        String phone,

        @NotNull
        @Valid
        @JsonProperty("shippingAddress")
        ShippingAddressEvent shippingAddress
) {
}
