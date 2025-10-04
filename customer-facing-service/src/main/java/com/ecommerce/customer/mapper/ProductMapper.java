package com.ecommerce.customer.mapper;

import com.ecommerce.customer.dto.CreateProductRequest;
import com.ecommerce.customer.dto.ProductDto;
import com.ecommerce.customer.dto.UpdateProductRequest;
import com.ecommerce.customer.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for Product entity and DTOs.
 * Configured as a Spring component for dependency injection.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ProductMapper {
    
    // Date conversion methods
    default java.time.LocalDateTime map(java.time.Instant instant) {
        return instant == null ? null : java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
    }
    
    default java.time.Instant map(java.time.LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    /**
     * Convert Product entity to ProductDto.
     *
     * @param product the entity
     * @return the DTO
     */
    @Mapping(target = "categoryId", source = "category.id")
    ProductDto toDto(Product product);

    /**
     * Convert list of Product entities to list of ProductDtos.
     *
     * @param products the entity list
     * @return the DTO list
     */
    java.util.List<ProductDto> toDtoList(java.util.List<Product> products);

    /**
     * Convert CreateProductRequest to Product entity.
     *
     * @param request the create request
     * @return the entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    Product toEntity(CreateProductRequest request);

    /**
     * Update an existing Product entity with values from UpdateProductRequest.
     * Only non-null values from the request are copied.
     *
     * @param request the update request
     * @param product the existing product to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sku", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntityFromDto(UpdateProductRequest request, @MappingTarget Product product);
}

