package com.ecommerce.order.payment;

/**
 * Interface for payment processing.
 * Implementations handle payment gateway integration.
 */
public interface PaymentService {

    /**
     * Process a payment for an order.
     * 
     * <p>This method is wrapped with circuit breaker for resilience.</p>
     *
     * @param request the payment request
     * @return payment result with transaction details
     * @throws PaymentException if payment processing fails
     */
    PaymentResult processPayment(PaymentRequest request) throws PaymentException;
}


