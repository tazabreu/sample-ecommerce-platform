package com.ecommerce.order.model;

/**
 * Order status enumeration representing the lifecycle of an order.
 * 
 * <p>State Transitions:</p>
 * <pre>
 * PENDING → PROCESSING → PAID → FULFILLED
 *    ↓          ↓
 * CANCELLED  FAILED
 * </pre>
 * 
 * <p>Status Descriptions:</p>
 * <ul>
 *   <li>PENDING: Order created, awaiting payment processing</li>
 *   <li>PROCESSING: Payment in progress</li>
 *   <li>PAID: Payment successful, ready for fulfillment</li>
 *   <li>FULFILLED: Order shipped/delivered</li>
 *   <li>CANCELLED: Order cancelled by customer or manager</li>
 *   <li>FAILED: Payment failed</li>
 * </ul>
 */
public enum OrderStatus {
    /**
     * Order created, awaiting payment processing.
     */
    PENDING,

    /**
     * Payment in progress.
     */
    PROCESSING,

    /**
     * Payment successful, ready for fulfillment.
     */
    PAID,

    /**
     * Order shipped/delivered.
     */
    FULFILLED,

    /**
     * Order cancelled.
     */
    CANCELLED,

    /**
     * Payment failed.
     */
    FAILED;

    /**
     * Checks if this status represents a terminal state (no further transitions).
     *
     * @return true if terminal state (FULFILLED, CANCELLED, FAILED), false otherwise
     */
    public boolean isTerminal() {
        return this == FULFILLED || this == CANCELLED || this == FAILED;
    }

    /**
     * Checks if this status represents a successful completion.
     *
     * @return true if FULFILLED, false otherwise
     */
    public boolean isSuccessful() {
        return this == FULFILLED;
    }

    /**
     * Checks if the order can be cancelled in this status.
     *
     * @return true if PENDING or PROCESSING, false otherwise
     */
    public boolean isCancellable() {
        return this == PENDING || this == PROCESSING;
    }

    /**
     * Checks if the order can be fulfilled in this status.
     *
     * @return true if PAID, false otherwise
     */
    public boolean isFulfillable() {
        return this == PAID;
    }
}

