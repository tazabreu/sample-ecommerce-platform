package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Order item information embedded in OrderCreatedEvent.
 * Contains product details and pricing snapshot at order time.
 */
public record OrderItemEvent(
        @NotNull
        @JsonProperty("productId")
        UUID productId,

        @NotBlank
        @JsonProperty("productSku")
        String productSku,

        @NotBlank
        @JsonProperty("productName")
        String productName,

        @NotNull
        @Min(1)
        @JsonProperty("quantity")
        Integer quantity,

        @NotNull
        @DecimalMin("0.01")
        @JsonProperty("priceSnapshot")
        BigDecimal priceSnapshot,

        @NotNull
        @DecimalMin("0.01")
        @JsonProperty("subtotal")
        BigDecimal subtotal
) {
}
