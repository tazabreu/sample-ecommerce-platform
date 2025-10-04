package com.ecommerce.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for creating a new product.
 * Matches OpenAPI schema for CreateProductRequest.
 */
public record CreateProductRequest(
        @NotBlank(message = "SKU is required")
        @Size(min = 1, max = 50, message = "SKU must be between 1 and 50 characters")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "SKU must contain only uppercase letters, numbers, and hyphens")
        String sku,

        @NotBlank(message = "Product name is required")
        @Size(min = 1, max = 200, message = "Product name must be between 1 and 200 characters")
        String name,

        @Size(max = 5000, message = "Product description must not exceed 5000 characters")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
        BigDecimal price,

        @NotNull(message = "Inventory quantity is required")
        @Min(value = 0, message = "Inventory quantity cannot be negative")
        Integer inventoryQuantity,

        @NotNull(message = "Category ID is required")
        UUID categoryId
) {
}


