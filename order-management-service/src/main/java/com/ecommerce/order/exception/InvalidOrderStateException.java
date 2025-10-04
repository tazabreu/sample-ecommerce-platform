package com.ecommerce.order.exception;

/**
 * Exception thrown when an operation is attempted on an order in an invalid state.
 * Results in HTTP 409 Conflict response.
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}


