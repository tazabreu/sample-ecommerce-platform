package com.ecommerce.customer.mapper;

import com.ecommerce.customer.dto.CategoryDto;
import com.ecommerce.customer.dto.CreateCategoryRequest;
import com.ecommerce.customer.dto.UpdateCategoryRequest;
import com.ecommerce.customer.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for Category entity and DTOs.
 * Configured as a Spring component for dependency injection.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface CategoryMapper {
    
    // Date conversion methods
    default java.time.LocalDateTime map(java.time.Instant instant) {
        return instant == null ? null : java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
    }
    
    default java.time.Instant map(java.time.LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    /**
     * Convert Category entity to CategoryDto.
     *
     * @param category the entity
     * @return the DTO
     */
    CategoryDto toDto(Category category);

    /**
     * Convert list of Category entities to list of CategoryDtos.
     *
     * @param categories the entity list
     * @return the DTO list
     */
    java.util.List<CategoryDto> toDtoList(java.util.List<Category> categories);

    /**
     * Convert CreateCategoryRequest to Category entity.
     *
     * @param request the create request
     * @return the entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "products", ignore = true)
    Category toEntity(CreateCategoryRequest request);

    /**
     * Update an existing Category entity with values from UpdateCategoryRequest.
     * Only non-null values from the request are copied.
     *
     * @param request the update request
     * @param category the existing category to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "products", ignore = true)
    void updateEntityFromDto(UpdateCategoryRequest request, @MappingTarget Category category);
}

