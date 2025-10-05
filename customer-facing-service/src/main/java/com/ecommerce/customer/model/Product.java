package com.ecommerce.customer.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Product entity representing a catalog item available for purchase.
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>Optimistic locking via @Version for inventory updates</li>
 *   <li>Soft delete via isActive flag</li>
 *   <li>SKU uniqueness constraint</li>
 *   <li>Price snapshot capture for cart items</li>
 * </ul>
 * 
 * <p>Validation Rules:</p>
 * <ul>
 *   <li>SKU: Required, 1-50 characters, unique, alphanumeric + hyphens</li>
 *   <li>Name: Required, 1-200 characters</li>
 *   <li>Description: Optional, max 5000 characters</li>
 *   <li>Price: Required, positive decimal (2 decimal places)</li>
 *   <li>Inventory: Required, non-negative integer</li>
 *   <li>Category: Required, must reference existing category</li>
 * </ul>
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_category", columnList = "category_id"),
    @Index(name = "idx_product_active", columnList = "is_active")
})
public class Product implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "SKU is required")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "SKU must contain only uppercase letters, digits, and hyphens")
    @Size(min = 1, max = 50, message = "SKU must be between 1 and 50 characters")
    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @NotNull(message = "Product name is required")
    @Size(min = 1, max = 200, message = "Product name must be between 1 and 200 characters")
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    @Column(name = "description", length = 5000)
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull(message = "Inventory quantity is required")
    @Min(value = 0, message = "Inventory quantity must be non-negative")
    @Column(name = "inventory_quantity", nullable = false)
    private Integer inventoryQuantity;

    @NotNull(message = "Category is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_category"))
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Category category;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

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
    protected Product() {
    }

    /**
     * Constructor for creating a new product.
     *
     * @param sku               the product SKU (required, unique)
     * @param name              the product name (required)
     * @param description       the product description (optional)
     * @param price             the product price (required, positive)
     * @param inventoryQuantity the initial inventory quantity (required, non-negative)
     * @param category          the product category (required)
     */
    public Product(String sku, String name, String description, BigDecimal price, 
                   Integer inventoryQuantity, Category category) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.inventoryQuantity = inventoryQuantity;
        this.category = category;
        this.isActive = true;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getInventoryQuantity() {
        return inventoryQuantity;
    }

    public void setInventoryQuantity(Integer inventoryQuantity) {
        this.inventoryQuantity = inventoryQuantity;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Business methods

    /**
     * Checks if the product is in stock.
     *
     * @return true if inventory quantity is greater than 0, false otherwise
     */
    @JsonIgnore
    public boolean isInStock() {
        return inventoryQuantity != null && inventoryQuantity > 0;
    }

    /**
     * Checks if the product can fulfill an order for the specified quantity.
     *
     * @param quantity the requested quantity
     * @return true if sufficient inventory is available, false otherwise
     */
    public boolean canFulfillOrder(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        return isInStock() && inventoryQuantity >= quantity;
    }

    /**
     * Decrements inventory by the specified quantity.
     * Uses optimistic locking to prevent overselling.
     *
     * @param quantity the quantity to decrement
     * @throws IllegalArgumentException if quantity is invalid or insufficient inventory
     */
    public void decrementInventory(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (!canFulfillOrder(quantity)) {
            throw new IllegalArgumentException(
                String.format("Insufficient inventory for product %s. Requested: %d, Available: %d",
                    sku, quantity, inventoryQuantity)
            );
        }
        this.inventoryQuantity -= quantity;
    }

    /**
     * Increments inventory by the specified quantity (e.g., for restocking).
     *
     * @param quantity the quantity to add
     * @throws IllegalArgumentException if quantity is not positive
     */
    public void incrementInventory(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.inventoryQuantity += quantity;
    }

    /**
     * Soft deletes the product by setting isActive to false.
     * Product remains in database for historical integrity.
     */
    public void discontinue() {
        this.isActive = false;
    }

    /**
     * Reactivates a discontinued product.
     */
    public void reactivate() {
        this.isActive = true;
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        Product product = (Product) o;
        return id != null && id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", sku='" + sku + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", inventoryQuantity=" + inventoryQuantity +
                ", isActive=" + isActive +
                ", version=" + version +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

