package com.ecommerce.order.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * PaymentTransaction entity tracking payment processing attempts and results.
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>One-to-One relationship with Order (unique constraint)</li>
 *   <li>Status transitions: PENDING → SUCCESS or FAILED</li>
 *   <li>External transaction ID tracking (for Stripe, PayPal, etc.)</li>
 *   <li>Failure reason capture for failed payments</li>
 *   <li>Retry attempt tracking</li>
 * </ul>
 * 
 * <p>Validation Rules:</p>
 * <ul>
 *   <li>Order: Required, unique (one payment per order)</li>
 *   <li>Amount: Required, positive decimal, must match order subtotal</li>
 *   <li>Status: Required, one of [PENDING, SUCCESS, FAILED]</li>
 *   <li>Payment Method: Required, one of [MOCK, STRIPE, PAYPAL, etc.]</li>
 *   <li>Attempt Count: Required, positive integer</li>
 * </ul>
 * 
 * <p>State Transitions:</p>
 * <pre>
 * PENDING → SUCCESS
 *    ↓
 * FAILED
 * </pre>
 */
@Table("payment_transactions")
public class PaymentTransaction implements Auditable, StatefulPersistable<UUID> {

    @Id
    private UUID id;

    @NotNull(message = "Order ID is required")
    @Column("order_id")
    private UUID orderId;

    @NotNull(message = "Payment amount is required")
    private BigDecimal amount;

    @NotNull(message = "Payment status is required")
    private PaymentStatus status = PaymentStatus.PENDING;

    @NotNull(message = "Payment method is required")
    @Size(min = 1, max = 50, message = "Payment method must be between 1 and 50 characters")
    private String paymentMethod;

    @Size(max = 100, message = "External transaction ID must not exceed 100 characters")
    private String externalTransactionId;

    private String failureReason;

    @NotNull(message = "Attempt count is required")
    @Min(value = 1, message = "Attempt count must be at least 1")
    private Integer attemptCount = 1;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant completedAt;

    @Transient
    private boolean isNew = true;

    // Constructors

    /**
     * Default constructor required by JPA.
     */
    protected PaymentTransaction() {
    }

    /**
     * Constructor for creating a new payment transaction.
     *
     * @param order         the order this payment is for (required, unique)
     * @param amount        the payment amount (required, must match order subtotal)
     * @param paymentMethod the payment method (e.g., MOCK, STRIPE, PAYPAL)
     */
    public PaymentTransaction(UUID orderId, BigDecimal amount, String paymentMethod) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method cannot be null or blank");
        }

        this.orderId = orderId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
        this.attemptCount = 1;
    }

    // Getters and Setters

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public void markPersisted() {
        this.isNew = false;
    }

    // Business methods

    /**
     * Marks the payment as successful.
     * Transitions status from PENDING to SUCCESS.
     *
     * @param externalTransactionId the external payment provider transaction ID
     * @throws IllegalStateException if payment is not in PENDING status
     */
    public void markAsSuccessful(String externalTransactionId) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                String.format("Cannot mark payment as successful. Current status: %s", status)
            );
        }
        if (externalTransactionId == null || externalTransactionId.isBlank()) {
            throw new IllegalArgumentException("External transaction ID is required for successful payment");
        }

        this.status = PaymentStatus.SUCCESS;
        this.externalTransactionId = externalTransactionId;
        this.completedAt = Instant.now();
    }

    /**
     * Marks the payment as failed.
     * Transitions status from PENDING to FAILED.
     *
     * @param failureReason the reason for payment failure
     * @throws IllegalStateException if payment is not in PENDING status
     */
    public void markAsFailed(String failureReason) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                String.format("Cannot mark payment as failed. Current status: %s", status)
            );
        }
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("Failure reason is required for failed payment");
        }

        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
        this.completedAt = Instant.now();
    }

    /**
     * Increments the attempt count (for retry scenarios).
     *
     * @return the new attempt count
     */
    public int incrementAttemptCount() {
        this.attemptCount++;
        return this.attemptCount;
    }

    /**
     * Checks if the payment is in a terminal state (SUCCESS or FAILED).
     *
     * @return true if terminal state, false otherwise
     */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    /**
     * Checks if the payment was successful.
     *
     * @return true if status is SUCCESS, false otherwise
     */
    public boolean isSuccessful() {
        return status == PaymentStatus.SUCCESS;
    }

    /**
     * Checks if the payment failed.
     *
     * @return true if status is FAILED, false otherwise
     */
    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    /**
     * Checks if the payment is still pending.
     *
     * @return true if status is PENDING, false otherwise
     */
    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    /**
     * Returns the processing duration (time from creation to completion).
     *
     * @return the duration in milliseconds, or null if not completed
     */
    public Long getProcessingDurationMillis() {
        if (completedAt != null && createdAt != null) {
            return completedAt.toEpochMilli() - createdAt.toEpochMilli();
        }
        return null;
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentTransaction)) return false;
        PaymentTransaction that = (PaymentTransaction) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PaymentTransaction{" +
                "id=" + id +
                ", amount=" + amount +
                ", status=" + status +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", externalTransactionId='" + externalTransactionId + '\'' +
                ", attemptCount=" + attemptCount +
                ", createdAt=" + createdAt +
                ", completedAt=" + completedAt +
                '}';
    }
}
