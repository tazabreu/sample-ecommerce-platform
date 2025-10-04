package com.ecommerce.customer.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for updating an existing category.
 * Matches OpenAPI schema for UpdateCategoryRequest.
 * All fields are optional for partial updates.
 */
public record UpdateCategoryRequest(
        @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
        String name,

        @Size(max = 1000, message = "Category description must not exceed 1000 characters")
        String description
) {
}


