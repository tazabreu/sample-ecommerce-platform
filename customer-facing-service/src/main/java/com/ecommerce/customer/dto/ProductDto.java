package com.ecommerce.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Product responses.
 * Matches OpenAPI schema for Product entity.
 */
public record ProductDto(
        @NotNull
        UUID id,

        @NotNull
        @Size(min = 1, max = 50)
        String sku,

        @NotNull
        @Size(min = 1, max = 200)
        String name,

        @Size(max = 5000)
        String description,

        @NotNull
        @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
        BigDecimal price,

        @NotNull
        @Min(value = 0, message = "Inventory quantity cannot be negative")
        Integer inventoryQuantity,

        @NotNull
        UUID categoryId,

        @NotNull
        Boolean isActive,

        @NotNull
        LocalDateTime createdAt,

        @NotNull
        LocalDateTime updatedAt
) {
}


