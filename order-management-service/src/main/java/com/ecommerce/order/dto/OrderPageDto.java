package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO for paginated order responses.
 * Matches OpenAPI schema for OrderPage.
 */
public record OrderPageDto(
        @NotNull
        List<OrderDto> orders,

        int currentPage,

        int pageSize,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last
) {
}

