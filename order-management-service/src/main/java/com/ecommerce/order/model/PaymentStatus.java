package com.ecommerce.order.model;

/**
 * Payment status enumeration representing the state of a payment transaction.
 * 
 * <p>State Transitions:</p>
 * <pre>
 * PENDING → SUCCESS
 *    ↓
 * FAILED
 * </pre>
 * 
 * <p>Status Descriptions:</p>
 * <ul>
 *   <li>PENDING: Payment initiated, awaiting result</li>
 *   <li>SUCCESS: Payment successful</li>
 *   <li>FAILED: Payment failed (final state)</li>
 * </ul>
 */
public enum PaymentStatus {
    /**
     * Payment initiated, awaiting result.
     */
    PENDING,

    /**
     * Payment successful.
     */
    SUCCESS,

    /**
     * Payment failed (final state).
     */
    FAILED;

    /**
     * Checks if this status represents a terminal state (no further transitions).
     *
     * @return true if SUCCESS or FAILED, false otherwise
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    /**
     * Checks if the payment was successful.
     *
     * @return true if SUCCESS, false otherwise
     */
    public boolean isSuccessful() {
        return this == SUCCESS;
    }

    /**
     * Checks if the payment failed.
     *
     * @return true if FAILED, false otherwise
     */
    public boolean isFailed() {
        return this == FAILED;
    }
}

