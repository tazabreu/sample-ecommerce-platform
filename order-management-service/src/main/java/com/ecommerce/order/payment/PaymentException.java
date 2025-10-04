package com.ecommerce.order.payment;

/**
 * Exception thrown when payment processing fails.
 */
public class PaymentException extends Exception {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}


