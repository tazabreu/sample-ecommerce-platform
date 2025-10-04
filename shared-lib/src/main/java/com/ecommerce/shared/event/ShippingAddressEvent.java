package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Shipping address information embedded in CustomerEvent.
 */
public record ShippingAddressEvent(
        @NotBlank
        @JsonProperty("street")
        String street,

        @NotBlank
        @JsonProperty("city")
        String city,

        @NotBlank
        @JsonProperty("state")
        String state,

        @NotBlank
        @JsonProperty("postalCode")
        String postalCode,

        @NotBlank
        @JsonProperty("country")
        String country
) {
}
