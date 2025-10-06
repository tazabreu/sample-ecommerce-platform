package com.ecommerce.order.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Order entity representing a customer's purchase request.
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>State machine with defined transitions (see OrderStatus)</li>
 *   <li>Denormalized customer info (guest checkout, no FK to customer table)</li>
 *   <li>JSONB storage for shipping address (flexible schema)</li>
 *   <li>PaymentTransaction tracked as separate aggregate (retrieved via repository)</li>
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
@Table("orders")
public class Order implements Auditable, StatefulPersistable<UUID> {

    @Id
    private UUID id;

    @NotNull(message = "Order number is required")
    @Pattern(regexp = "^ORD-\\d{8}-\\d{3}$", message = "Order number must match format ORD-YYYYMMDD-NNN")
    private String orderNumber;

    @NotNull(message = "Customer name is required")
    @Size(min = 1, max = 200, message = "Customer name must be between 1 and 200 characters")
    private String customerName;

    @NotNull(message = "Customer email is required")
    @Size(min = 1, max = 100, message = "Customer email must be between 1 and 100 characters")
    private String customerEmail;

    @NotNull(message = "Customer phone is required")
    @Size(min = 1, max = 20, message = "Customer phone must be between 1 and 20 characters")
    private String customerPhone;

    // JSONB storage: Custom converters handle ShippingAddress <-> PGobject conversion
    @NotNull(message = "Shipping address is required")
    @Column("shipping_address")
    private ShippingAddress shippingAddress;


    @NotNull(message = "Subtotal is required")
    private BigDecimal subtotal;

    @NotNull(message = "Order status is required")
    private OrderStatus status = OrderStatus.PENDING;

    @MappedCollection(idColumn = "order_id")
    private Set<OrderItem> items = new HashSet<>();

    private Instant createdAt;

    private Instant updatedAt;

    private Instant completedAt;

    @Transient
    private boolean isNew = true;

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
     * @param shippingAddress the shipping address
     * @param subtotal        the order subtotal
     */
    public Order(String orderNumber, String customerName, String customerEmail,
                 String customerPhone, ShippingAddress shippingAddress, BigDecimal subtotal) {
        setOrderNumber(orderNumber);
        setCustomerName(customerName);
        setCustomerEmail(customerEmail);
        setCustomerPhone(customerPhone);
        setShippingAddress(shippingAddress);
        setSubtotal(subtotal);
        this.status = OrderStatus.PENDING;
    }

    // Getters and Setters

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(ShippingAddress shippingAddress) {
        this.shippingAddress = shippingAddress;
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
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public void setItems(Set<OrderItem> items) {
        this.items = items != null ? new HashSet<>(items) : new HashSet<>();
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
