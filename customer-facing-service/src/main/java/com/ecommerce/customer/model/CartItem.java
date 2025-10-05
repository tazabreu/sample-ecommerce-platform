package com.ecommerce.customer.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
@Entity
@Table(name = "cart_items",
    indexes = {
        @Index(name = "idx_cartitem_cart", columnList = "cart_id"),
        @Index(name = "idx_cartitem_product", columnList = "product_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_product", columnNames = {"cart_id", "product_id"})
    }
)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartItem implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Cart is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cartitem_cart"))
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Cart cart;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cartitem_product"))
    private Product product;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull(message = "Price snapshot is required")
    @Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
    public CartItem(Cart cart, Product product, int quantity, BigDecimal priceSnapshot) {
        if (cart == null) {
            throw new IllegalArgumentException("Cart cannot be null");
        }
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (priceSnapshot == null || priceSnapshot.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price snapshot must be positive");
        }

        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
        this.priceSnapshot = priceSnapshot;
        this.subtotal = calculateSubtotal();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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

    /**
     * Returns the product SKU for convenience.
     *
     * @return the product SKU
     */
    public String getProductSku() {
        return product != null ? product.getSku() : null;
    }

    /**
     * Returns the product name for convenience.
     *
     * @return the product name
     */
    public String getProductName() {
        return product != null ? product.getName() : null;
    }

    /**
     * Checks if the price snapshot matches the current product price.
     *
     * @return true if prices match, false otherwise
     */
    public boolean isPriceUpToDate() {
        return product != null && priceSnapshot.compareTo(product.getPrice()) == 0;
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
                ", productSku='" + getProductSku() + '\'' +
                ", productName='" + getProductName() + '\'' +
                ", quantity=" + quantity +
                ", priceSnapshot=" + priceSnapshot +
                ", subtotal=" + subtotal +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

