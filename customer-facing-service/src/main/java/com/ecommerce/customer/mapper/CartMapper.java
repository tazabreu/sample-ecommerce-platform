package com.ecommerce.customer.mapper;

import com.ecommerce.customer.dto.CartDto;
import com.ecommerce.customer.dto.CartItemDto;
import com.ecommerce.customer.model.Cart;
import com.ecommerce.customer.model.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * MapStruct mapper for Cart and CartItem entities and DTOs.
 * Configured as a Spring component for dependency injection.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface CartMapper {
    
    // Date conversion methods
    default java.time.LocalDateTime map(java.time.Instant instant) {
        return instant == null ? null : java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
    }
    
    default java.time.Instant map(java.time.LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    /**
     * Convert Cart entity to CartDto.
     *
     * @param cart the entity
     * @return the DTO
     */
    CartDto toDto(Cart cart);

    /**
     * Convert CartItem entity to CartItemDto.
     *
     * @param cartItem the entity
     * @return the DTO
     */
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productSku", source = "product.sku")
    @Mapping(target = "productName", source = "product.name")
    CartItemDto toCartItemDto(CartItem cartItem);

    /**
     * Convert list of CartItem entities to list of CartItemDtos.
     *
     * @param cartItems the entity list
     * @return the DTO list
     */
    List<CartItemDto> toCartItemDtoList(List<CartItem> cartItems);
}

