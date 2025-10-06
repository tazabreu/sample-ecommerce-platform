package com.ecommerce.customer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CartItem entity representing an individual product entry in a shopping cart.
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>Price snapshot captured at time of adding to cart</li>
 *   <li>Unique constraint: one entry per product per cart</li>
 *   <li>Subtotal automatically calculated from quantity and price snapshot</li>
 * </ul>
 * 
 * <p>Validation Rules:</p>
 * <ul>
 *   <li>Cart: Required, must reference existing cart</li>
 *   <li>Product: Required, must reference existing product</li>
 *   <li>Quantity: Required, positive integer</li>
 *   <li>Price Snapshot: Required, positive decimal (captured at add time)</li>
 *   <li>Subtotal: Calculated field (quantity × priceSnapshot)</li>
 * </ul>
 * 
 * <p>Relationships:</p>
 * <ul>
 *   <li>Many-to-One: Many cart items belong to one cart</li>
 *   <li>Many-to-One: Many cart items reference one product</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Table("cart_items")
public class CartItem implements Auditable, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private UUID id;

    @NotNull(message = "Product ID is required")
    @Column("product_id")
    private UUID productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Price snapshot is required")
    private BigDecimal priceSnapshot;

    private BigDecimal subtotal;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private String productSku;

    @Transient
    private String productName;

    // Constructors

    /**
     * Default constructor required by JPA.
     */
    protected CartItem() {
    }

    /**
     * Constructor for creating a new cart item.
     *
     * @param cart          the cart this item belongs to (required)
     * @param product       the product being added (required)
     * @param quantity      the item quantity (required, positive)
     * @param priceSnapshot the product price at time of adding (required)
     */
    public CartItem(UUID productId, int quantity, BigDecimal priceSnapshot) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (priceSnapshot == null || priceSnapshot.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price snapshot must be positive");
        }

        this.productId = productId;
        this.quantity = quantity;
        this.priceSnapshot = priceSnapshot;
        this.subtotal = calculateSubtotal();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }


    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.quantity = quantity;
        this.subtotal = calculateSubtotal();
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public void setPriceSnapshot(BigDecimal priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
        this.subtotal = calculateSubtotal();
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    @Transient
    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    @Transient
    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
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

    // Business methods

    /**
     * Calculates the subtotal for this cart item.
     * Subtotal = quantity × priceSnapshot
     *
     * @return the calculated subtotal
     */
    public BigDecimal calculateSubtotal() {
        if (quantity != null && priceSnapshot != null) {
            this.subtotal = priceSnapshot.multiply(BigDecimal.valueOf(quantity));
            return this.subtotal;
        }
        return BigDecimal.ZERO;
    }

    /**
     * Increments the quantity by the specified amount.
     *
     * @param additionalQuantity the quantity to add (must be positive)
     */
    public void incrementQuantity(int additionalQuantity) {
        if (additionalQuantity <= 0) {
            throw new IllegalArgumentException("Additional quantity must be positive");
        }
        this.quantity += additionalQuantity;
        this.subtotal = calculateSubtotal();
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartItem)) return false;
        CartItem cartItem = (CartItem) o;
        return id != null && id.equals(cartItem.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "CartItem{" +
                "id=" + id +
                ", productId=" + productId +
                ", quantity=" + quantity +
                ", priceSnapshot=" + priceSnapshot +
                ", subtotal=" + subtotal +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
