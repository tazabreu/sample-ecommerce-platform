package com.ecommerce.order.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order entity representing a customer's purchase request.
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>State machine with defined transitions (see OrderStatus)</li>
 *   <li>Denormalized customer info (guest checkout, no FK to customer table)</li>
 *   <li>JSONB storage for shipping address (flexible schema)</li>
 *   <li>One-to-One relationship with PaymentTransaction</li>
 * </ul>
 * 
 * <p>Validation Rules:</p>
 * <ul>
 *   <li>Order Number: Required, unique, format ORD-YYYYMMDD-NNN</li>
 *   <li>Customer Name: Required, 1-200 characters</li>
 *   <li>Customer Email: Required, valid email, max 100 characters</li>
 *   <li>Customer Phone: Required, max 20 characters</li>
 *   <li>Shipping Address: Required JSON with street, city, state, postalCode, country</li>
 *   <li>Subtotal: Required, positive decimal</li>
 * </ul>
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_number", columnList = "order_number", unique = true),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_created", columnList = "created_at"),
    @Index(name = "idx_order_email", columnList = "customer_email")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Order number is required")
    @Pattern(regexp = "^ORD-\\d{8}-\\d{3}$", message = "Order number must match format ORD-YYYYMMDD-NNN")
    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @NotNull(message = "Customer name is required")
    @Size(min = 1, max = 200, message = "Customer name must be between 1 and 200 characters")
    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @NotNull(message = "Customer email is required")
    @Size(min = 1, max = 100, message = "Customer email must be between 1 and 100 characters")
    @Column(name = "customer_email", nullable = false, length = 100)
    private String customerEmail;

    @NotNull(message = "Customer phone is required")
    @Size(min = 1, max = 20, message = "Customer phone must be between 1 and 20 characters")
    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @NotNull(message = "Shipping address is required")
    @Column(name = "shipping_address", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> shippingAddress;

    @NotNull(message = "Subtotal is required")
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @NotNull(message = "Order status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PaymentTransaction paymentTransaction;

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
    protected Order() {
    }

    /**
     * Constructor for creating a new order.
     *
     * @param orderNumber     the unique order number (format ORD-YYYYMMDD-NNN)
     * @param customerName    the customer's full name
     * @param customerEmail   the customer's email
     * @param customerPhone   the customer's phone number
     * @param shippingAddress the shipping address (JSONB map)
     * @param subtotal        the order subtotal
     */
    public Order(String orderNumber, String customerName, String customerEmail,
                 String customerPhone, Map<String, String> shippingAddress, BigDecimal subtotal) {
        setOrderNumber(orderNumber);
        setCustomerName(customerName);
        setCustomerEmail(customerEmail);
        setCustomerPhone(customerPhone);
        setShippingAddress(shippingAddress);
        setSubtotal(subtotal);
        this.status = OrderStatus.PENDING;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public Map<String, String> getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(Map<String, String> shippingAddress) {
        this.shippingAddress = shippingAddress != null ? Map.copyOf(shippingAddress) : Collections.emptyMap();
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public PaymentTransaction getPaymentTransaction() {
        return paymentTransaction;
    }

    public void setPaymentTransaction(PaymentTransaction paymentTransaction) {
        this.paymentTransaction = paymentTransaction;
        if (paymentTransaction != null && paymentTransaction.getOrder() != this) {
            paymentTransaction.setOrder(this);
        }
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

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    // Business methods (State Machine)

    /**
     * Checks if the order can be cancelled in its current state.
     *
     * @return true if status is PENDING or PROCESSING, false otherwise
     */
    public boolean canBeCancelled() {
        return status.isCancellable();
    }

    /**
     * Marks the order as paid (payment successful).
     * Transitions status from PROCESSING to PAID.
     *
     * @throws IllegalStateException if current status is not PROCESSING
     */
    public void markAsPaid() {
        if (status != OrderStatus.PROCESSING) {
            throw new IllegalStateException(
                String.format("Cannot mark order as paid. Current status: %s", status)
            );
        }
        this.status = OrderStatus.PAID;
    }

    /**
     * Marks the order as failed (payment failed).
     * Transitions status from PROCESSING to FAILED.
     *
     * @throws IllegalStateException if current status is not PROCESSING
     */
    public void markAsFailed() {
        if (status != OrderStatus.PROCESSING) {
            throw new IllegalStateException(
                String.format("Cannot mark order as failed. Current status: %s", status)
            );
        }
        this.status = OrderStatus.FAILED;
    }

    /**
     * Marks the order as processing (payment initiated).
     * Transitions status from PENDING to PROCESSING.
     *
     * @throws IllegalStateException if current status is not PENDING
     */
    public void markAsProcessing() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                String.format("Cannot mark order as processing. Current status: %s", status)
            );
        }
        this.status = OrderStatus.PROCESSING;
    }

    /**
     * Marks the order as fulfilled (shipped/delivered).
     * Transitions status from PAID to FULFILLED.
     *
     * @throws IllegalStateException if current status is not PAID
     */
    public void markAsFulfilled() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException(
                String.format("Cannot fulfill order. Current status: %s", status)
            );
        }
        this.status = OrderStatus.FULFILLED;
        this.completedAt = Instant.now();
    }

    /**
     * Cancels the order.
     * Transitions status from PENDING or PROCESSING to CANCELLED.
     *
     * @throws IllegalStateException if order cannot be cancelled
     */
    public void cancel() {
        if (!canBeCancelled()) {
            throw new IllegalStateException(
                String.format("Cannot cancel order. Current status: %s", status)
            );
        }
        this.status = OrderStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    /**
     * Adds an order item to this order.
     *
     * @param item the order item to add
     */
    public void addItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Order item cannot be null");
        }
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Returns the payment status from the associated payment transaction.
     *
     * @return the payment status, or null if no payment transaction exists
     */
    public PaymentStatus getPaymentStatus() {
        return paymentTransaction != null ? paymentTransaction.getStatus() : null;
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;
        Order order = (Order) o;
        return id != null && id.equals(order.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", orderNumber='" + orderNumber + '\'' +
                ", customerEmail='" + customerEmail + '\'' +
                ", subtotal=" + subtotal +
                ", status=" + status +
                ", itemCount=" + items.size() +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
