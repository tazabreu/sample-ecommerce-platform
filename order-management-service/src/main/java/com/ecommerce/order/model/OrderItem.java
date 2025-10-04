package com.ecommerce.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * OrderItem entity representing a product entry within an order.
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>Immutable: Once order is created, items never change (historical record)</li>
 *   <li>Denormalized: Product details (SKU, name, price) captured at order time</li>
 *   <li>No FK to Product: Product may be deleted/modified, but order item remains unchanged</li>
 * </ul>
 * 
 * <p>Design Rationale:</p>
 * <ul>
 *   <li>Historical integrity: Order reflects products as they were at purchase time</li>
 *   <li>Audit trail: Price and product details frozen at order creation</li>
 *   <li>Data independence: Order service doesn't depend on product service data</li>
 * </ul>
 * 
 * <p>Validation Rules:</p>
 * <ul>
 *   <li>Order: Required, must reference existing order</li>
 *   <li>Product ID: Required (stored as UUID, not enforced as FK)</li>
 *   <li>Product SKU: Required, 1-50 characters</li>
 *   <li>Product Name: Required, 1-200 characters</li>
 *   <li>Quantity: Required, positive integer</li>
 *   <li>Price Snapshot: Required, positive decimal</li>
 *   <li>Subtotal: Calculated field (quantity × priceSnapshot)</li>
 * </ul>
 */
@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_orderitem_order", columnList = "order_id"),
    @Index(name = "idx_orderitem_product", columnList = "product_id")
})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Order is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orderitem_order"))
    private Order order;

    @NotNull(message = "Product ID is required")
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @NotNull(message = "Product SKU is required")
    @Size(min = 1, max = 50, message = "Product SKU must be between 1 and 50 characters")
    @Column(name = "product_sku", nullable = false, length = 50)
    private String productSku;

    @NotNull(message = "Product name is required")
    @Size(min = 1, max = 200, message = "Product name must be between 1 and 200 characters")
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull(message = "Price snapshot is required")
    @Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;

    @NotNull(message = "Subtotal is required")
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructors

    /**
     * Default constructor required by JPA.
     */
    protected OrderItem() {
    }

    /**
     * Constructor for creating a new order item.
     * 
     * <p>Note: This entity is immutable after creation. No update timestamp is maintained.</p>
     *
     * @param order         the order this item belongs to (required)
     * @param productId     the product ID (reference, not enforced as FK)
     * @param productSku    the product SKU snapshot
     * @param productName   the product name snapshot
     * @param quantity      the item quantity (required, positive)
     * @param priceSnapshot the product price at order time
     */
    public OrderItem(Order order, UUID productId, String productSku, String productName,
                     int quantity, BigDecimal priceSnapshot) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }
        if (productSku == null || productSku.isBlank()) {
            throw new IllegalArgumentException("Product SKU cannot be null or blank");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be null or blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (priceSnapshot == null || priceSnapshot.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price snapshot must be positive");
        }

        this.order = order;
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.quantity = quantity;
        this.priceSnapshot = priceSnapshot;
        this.subtotal = calculateSubtotal(quantity, priceSnapshot);
    }

    // Getters (no setters - immutable)

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    // Package-private setter for bidirectional relationship management
    void setOrder(Order order) {
        this.order = order;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Business methods

    /**
     * Calculates the subtotal for this order item.
     * Subtotal = quantity × priceSnapshot
     *
     * @param quantity      the item quantity
     * @param priceSnapshot the price at order time
     * @return the calculated subtotal
     */
    private static BigDecimal calculateSubtotal(int quantity, BigDecimal priceSnapshot) {
        return priceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Returns a formatted string representation of the price snapshot.
     *
     * @return the price snapshot as a formatted currency string
     */
    public String getFormattedPrice() {
        return String.format("$%.2f", priceSnapshot);
    }

    /**
     * Returns a formatted string representation of the subtotal.
     *
     * @return the subtotal as a formatted currency string
     */
    public String getFormattedSubtotal() {
        return String.format("$%.2f", subtotal);
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem)) return false;
        OrderItem orderItem = (OrderItem) o;
        return id != null && id.equals(orderItem.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "id=" + id +
                ", productId=" + productId +
                ", productSku='" + productSku + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", priceSnapshot=" + priceSnapshot +
                ", subtotal=" + subtotal +
                ", createdAt=" + createdAt +
                '}';
    }
}

