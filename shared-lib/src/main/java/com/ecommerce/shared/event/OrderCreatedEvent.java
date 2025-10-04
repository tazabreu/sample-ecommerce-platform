package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event published when an order is created during checkout.
 * Published to Kafka topic: orders.created
 *
 * <p>This event contains all information needed by the order management service
 * to create an order, process payment, and fulfill the order.</p>
 *
 * <p>Schema Version: 1.0</p>
 *
 * @see com.ecommerce.shared.event.OrderItemEvent
 * @see com.ecommerce.shared.event.CustomerEvent
 */
public record OrderCreatedEvent(
        @NotNull
        @JsonProperty("eventId")
        UUID eventId,

        @NotBlank
        @JsonProperty("eventType")
        String eventType,

        @NotBlank
        @JsonProperty("eventVersion")
        String eventVersion,

        @NotNull
        @JsonProperty("timestamp")
        Instant timestamp,

        @NotBlank
        @JsonProperty("correlationId")
        String correlationId,

        @NotNull
        @JsonProperty("orderId")
        UUID orderId,

        @NotBlank
        @JsonProperty("orderNumber")
        String orderNumber,

        @NotNull
        @Valid
        @JsonProperty("customer")
        CustomerEvent customer,

        @NotEmpty
        @Valid
        @JsonProperty("items")
        List<OrderItemEvent> items,

        @NotNull
        @DecimalMin("0.01")
        @JsonProperty("subtotal")
        BigDecimal subtotal,

        @NotNull
        @JsonProperty("cartId")
        UUID cartId
) {
    /**
     * Create a new OrderCreatedEvent with default event type and version.
     *
     * @param correlationId request correlation ID
     * @param orderId order UUID
     * @param orderNumber human-readable order number
     * @param customer customer information
     * @param items order items
     * @param subtotal order subtotal
     * @param cartId cart UUID
     */
    public OrderCreatedEvent(
            String correlationId,
            UUID orderId,
            String orderNumber,
            CustomerEvent customer,
            List<OrderItemEvent> items,
            BigDecimal subtotal,
            UUID cartId
    ) {
        this(
                UUID.randomUUID(),
                "ORDER_CREATED",
                "1.0",
                Instant.now(),
                correlationId,
                orderId,
                orderNumber,
                customer,
                items,
                subtotal,
                cartId
        );
    }
}
