package com.ecommerce.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_payment_order", columnList = "order_id", unique = true),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_external", columnList = "external_transaction_id")
})
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Order is required")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_payment_order"))
    private Order order;

    @NotNull(message = "Payment amount is required")
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Payment status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @NotNull(message = "Payment method is required")
    @Size(min = 1, max = 50, message = "Payment method must be between 1 and 50 characters")
    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    @Size(max = 100, message = "External transaction ID must not exceed 100 characters")
    @Column(name = "external_transaction_id", length = 100)
    private String externalTransactionId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @NotNull(message = "Attempt count is required")
    @Min(value = 1, message = "Attempt count must be at least 1")
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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
    public PaymentTransaction(Order order, BigDecimal amount, String paymentMethod) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method cannot be null or blank");
        }

        this.order = order;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
        this.attemptCount = 1;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
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

