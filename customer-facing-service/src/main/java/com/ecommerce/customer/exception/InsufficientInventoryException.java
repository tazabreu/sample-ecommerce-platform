package com.ecommerce.customer.exception;

import java.util.UUID;

/**
 * Exception thrown when there is insufficient inventory to fulfill an order.
 * Results in HTTP 409 Conflict response.
 */
public class InsufficientInventoryException extends RuntimeException {

    private final UUID productId;
    private final Integer requestedQuantity;
    private final Integer availableQuantity;

    public InsufficientInventoryException(UUID productId, Integer requestedQuantity, Integer availableQuantity) {
        super(String.format(
                "Insufficient inventory for product %s. Requested: %d, Available: %d",
                productId, requestedQuantity, availableQuantity
        ));
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public UUID getProductId() {
        return productId;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }
}


