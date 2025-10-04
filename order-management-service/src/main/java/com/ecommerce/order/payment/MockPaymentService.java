package com.ecommerce.order.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Mock implementation of PaymentService for testing and demonstration.
 * 
 * <p>This service simulates payment processing with configurable success/failure.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Always returns success with mock transaction ID</li>
 *   <li>Wrapped with circuit breaker for resilience testing</li>
 *   <li>Simulates processing time (100ms delay)</li>
 * </ul>
 * 
 * <p>Production Note: Replace with StripePaymentService or similar.</p>
 */
@Service
public class MockPaymentService implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(MockPaymentService.class);

    /**
     * Process a payment (mock implementation).
     * Always succeeds with a mock transaction ID.
     *
     * @param request the payment request
     * @return payment result with success and mock transaction ID
     * @throws PaymentException never thrown in mock implementation
     */
    @Override
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public PaymentResult processPayment(PaymentRequest request) throws PaymentException {
        logger.info("Processing mock payment - orderNumber: {}, amount: {}", 
                request.orderNumber(), request.amount());

        // Simulate processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Payment processing interrupted", e);
        }

        // Generate mock transaction ID
        String mockTransactionId = "mock_tx_" + Instant.now().getEpochSecond();

        logger.info("Mock payment successful - orderNumber: {}, transactionId: {}", 
                request.orderNumber(), mockTransactionId);

        return PaymentResult.success(mockTransactionId);
    }

    /**
     * Fallback method for circuit breaker.
     * Returns a failure result when payment service is unavailable.
     *
     * @param request the payment request
     * @param ex the exception that triggered the fallback
     * @return payment result with failure
     */
    @SuppressWarnings("unused")
    private PaymentResult processPaymentFallback(PaymentRequest request, Throwable ex) {
        logger.error("Payment service circuit breaker triggered - orderNumber: {}, error: {}", 
                request.orderNumber(), ex.getMessage());

        return PaymentResult.failure(
                "Payment service unavailable (circuit breaker open): " + ex.getMessage()
        );
    }
}


