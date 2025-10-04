package com.ecommerce.customer.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO for paginated product responses.
 * Matches OpenAPI schema for ProductPage.
 */
public record ProductPageDto(
        @NotNull
        List<ProductDto> content,

        @NotNull
        Long totalElements,

        @NotNull
        Integer totalPages,

        @NotNull
        Integer size,

        @NotNull
        Integer number
) {
}


