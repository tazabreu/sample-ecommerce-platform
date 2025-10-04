package com.ecommerce.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new category.
 * Matches OpenAPI schema for CreateCategoryRequest.
 */
public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
        String name,

        @Size(max = 1000, message = "Category description must not exceed 1000 characters")
        String description
) {
}


