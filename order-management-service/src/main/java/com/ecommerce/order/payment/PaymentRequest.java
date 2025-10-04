package com.ecommerce.order.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to process a payment.
 * Contains order details and payment amount.
 */
public record PaymentRequest(
        UUID orderId,
        String orderNumber,
        BigDecimal amount,
        String customerEmail
) {
}


