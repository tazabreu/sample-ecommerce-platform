package com.ecommerce.order.exception;

/**
 * Exception thrown when a requested resource is not found.
 * Results in HTTP 404 Not Found response.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Object id) {
        super(String.format("%s not found with id: %s", resourceType, id));
    }
}


