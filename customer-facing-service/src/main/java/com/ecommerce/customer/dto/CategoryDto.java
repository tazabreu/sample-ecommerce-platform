package com.ecommerce.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Category responses.
 * Matches OpenAPI schema for Category entity.
 */
public record CategoryDto(
        @NotNull
        UUID id,

        @NotNull
        @Size(min = 1, max = 100)
        String name,

        @Size(max = 1000)
        String description,

        @NotNull
        LocalDateTime createdAt,

        @NotNull
        LocalDateTime updatedAt
) {
}


