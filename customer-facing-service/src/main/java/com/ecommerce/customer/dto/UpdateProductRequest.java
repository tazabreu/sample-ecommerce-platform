package com.ecommerce.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for updating an existing product.
 * Matches OpenAPI schema for UpdateProductRequest.
 * All fields are optional for partial updates.
 */
public record UpdateProductRequest(
        @Size(min = 1, max = 200, message = "Product name must be between 1 and 200 characters")
        String name,

        @Size(max = 5000, message = "Product description must not exceed 5000 characters")
        String description,

        @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
        BigDecimal price,

        @Min(value = 0, message = "Inventory quantity cannot be negative")
        Integer inventoryQuantity,

        UUID categoryId,

        Boolean isActive
) {
}


