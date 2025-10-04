package com.ecommerce.order.mapper;

import com.ecommerce.order.dto.CustomerInfoDto;
import com.ecommerce.order.dto.OrderDto;
import com.ecommerce.order.dto.OrderItemDto;
import com.ecommerce.order.dto.ShippingAddressDto;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * MapStruct mapper for Order and OrderItem entities and DTOs.
 * Configured as a Spring component for dependency injection.
 * Handles JSONB conversion for shipping address.
 */
@Mapper(
        componentModel = "spring"
)
public abstract class OrderMapper {

    /**
     * Convert Order entity to OrderDto.
     *
     * @param order the entity
     * @return the DTO
     */
    @Mapping(target = "customerInfo", source = "order", qualifiedByName = "mapCustomerInfo")
    @Mapping(target = "status", expression = "java(order.getStatus().name())")
    @Mapping(target = "paymentStatus", expression = "java(order.getPaymentTransaction() != null ? order.getPaymentTransaction().getStatus().name() : null)")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantToLocalDateTime")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "instantToLocalDateTime")
    @Mapping(target = "completedAt", source = "completedAt", qualifiedByName = "instantToLocalDateTime")
    public abstract OrderDto toDto(Order order);

    /**
     * Convert OrderItem entity to OrderItemDto.
     *
     * @param orderItem the entity
     * @return the DTO
     */
    public abstract OrderItemDto toOrderItemDto(OrderItem orderItem);

    /**
     * Convert list of OrderItem entities to list of OrderItemDtos.
     *
     * @param orderItems the entity list
     * @return the DTO list
     */
    public abstract List<OrderItemDto> toOrderItemDtoList(List<OrderItem> orderItems);

    /**
     * Map Order entity fields to CustomerInfoDto.
     * Extracts shipping address from JSONB field.
     *
     * @param order the order entity
     * @return the customer info DTO
     */
    @Named("mapCustomerInfo")
    protected CustomerInfoDto mapCustomerInfo(Order order) {
        ShippingAddressDto shippingAddress = mapShippingAddress(order.getShippingAddress());
        return new CustomerInfoDto(
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getCustomerPhone(),
                shippingAddress
        );
    }

    @Named("instantToLocalDateTime")
    protected LocalDateTime instantToLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    /**
     * Convert JSONB Map to ShippingAddressDto.
     *
     * @param addressMap the address as a map
     * @return the shipping address DTO
     */
    protected ShippingAddressDto mapShippingAddress(Map<String, String> addressMap) {
        if (addressMap == null) {
            return null;
        }
        return new ShippingAddressDto(
                addressMap.get("street"),
                addressMap.get("city"),
                addressMap.get("state"),
                addressMap.get("postalCode"),
                addressMap.get("country")
        );
    }

    /**
     * Convert ShippingAddressDto to JSONB Map.
     *
     * @param address the shipping address DTO
     * @return the address as a map for JSONB storage
     */
    protected Map<String, String> mapShippingAddressToMap(ShippingAddressDto address) {
        if (address == null) {
            return null;
        }
        return Map.of(
                "street", address.street(),
                "city", address.city(),
                "state", address.state(),
                "postalCode", address.postalCode(),
                "country", address.country()
        );
    }
}

