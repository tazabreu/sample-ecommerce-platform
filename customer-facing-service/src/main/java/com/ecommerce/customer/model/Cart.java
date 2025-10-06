package com.ecommerce.customer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cart entity representing a temporary shopping cart for guest checkout.
 * 
 * <p>Storage Strategy:</p>
 * <ul>
 *   <li>Redis: Primary storage (in-memory, fast access, TTL-based expiration)</li>
 *   <li>PostgreSQL: Secondary storage (analytics, abandoned cart recovery)</li>
 * </ul>
 * 
 * <p>Validation Rules:</p>
 * <ul>
 *   <li>SessionId: Required, unique, 1-100 characters</li>
 *   <li>Subtotal: Calculated field (sum of all CartItem subtotals)</li>
 * </ul>
 * 
 * <p>Relationships:</p>
 * <ul>
 *   <li>One-to-Many: A cart contains multiple cart items</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Table("carts")
public class Cart implements Auditable, Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private UUID id;

    @NotNull(message = "Session ID is required")
    @Size(min = 1, max = 100, message = "Session ID must be between 1 and 100 characters")
    private String sessionId;

    private BigDecimal subtotal = BigDecimal.ZERO;

    @MappedCollection(idColumn = "cart_id", keyColumn = "item_index")
    private List<CartItem> items = new ArrayList<>();

    private Instant createdAt;

    private Instant updatedAt;

    private Instant expiresAt;

    // Constructors

    /**
     * Default constructor required by JPA.
     */
    protected Cart() {
    }

    /**
     * Constructor for creating a new cart.
     *
     * @param sessionId the session identifier (required, unique)
     * @param ttlMinutes the time-to-live in minutes for cart expiration
     */
    public Cart(String sessionId, int ttlMinutes) {
        this.sessionId = sessionId;
        this.expiresAt = Instant.now().plusSeconds(ttlMinutes * 60L);
        this.subtotal = BigDecimal.ZERO;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }


    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    // Business methods

    /**
     * Adds a new item to the cart or updates quantity if product already exists.
     *
     * @param product  the product to add
     * @param quantity the quantity to add
     * @return the added or updated CartItem
     */
    public CartItem addItem(Product product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        // Check if product already exists in cart
        CartItem existingItem = items.stream()
            .filter(item -> item.getProductId().equals(product.getId()))
            .findFirst()
            .orElse(null);

        if (existingItem != null) {
            // Update existing item quantity
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            existingItem.calculateSubtotal();
        } else {
            // Create new cart item
            CartItem newItem = new CartItem(product.getId(), quantity, product.getPrice());
            items.add(newItem);
            existingItem = newItem;
        }

        calculateSubtotal();
        return existingItem;
    }

    /**
     * Removes an item from the cart.
     *
     * @param cartItemId the cart item ID to remove
     * @return true if item was removed, false if not found
     */
    public boolean removeItem(UUID cartItemId) {
        boolean removed = items.removeIf(item -> cartItemId.equals(item.getId()));
        if (removed) {
            calculateSubtotal();
        }
        return removed;
    }

    /**
     * Updates the quantity of an existing cart item.
     *
     * @param cartItemId the cart item ID to update
     * @param quantity   the new quantity
     * @return the updated CartItem
     * @throws IllegalArgumentException if item not found or invalid quantity
     */
    public CartItem updateItemQuantity(UUID cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        CartItem item = items.stream()
            .filter(i -> cartItemId.equals(i.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + cartItemId));

        item.setQuantity(quantity);
        item.calculateSubtotal();
        calculateSubtotal();

        return item;
    }

    /**
     * Removes all items from the cart.
     */
    public void clear() {
        items.clear();
        subtotal = BigDecimal.ZERO;
    }

    /**
     * Calculates and updates the cart subtotal from all cart items.
     */
    public void calculateSubtotal() {
        this.subtotal = items.stream()
            .map(CartItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Checks if the cart is empty.
     *
     * @return true if cart has no items, false otherwise
     */
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    /**
     * Checks if the cart has expired based on expiresAt timestamp.
     *
     * @return true if cart has expired, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Extends the cart expiration time.
     *
     * @param additionalMinutes the additional minutes to add to expiration
     */
    public void extendExpiration(int additionalMinutes) {
        this.expiresAt = this.expiresAt.plusSeconds(additionalMinutes * 60L);
    }

    /**
     * Returns the total number of items in the cart.
     *
     * @return the sum of all item quantities
     */
    public int getTotalItemCount() {
        return items.stream()
            .mapToInt(CartItem::getQuantity)
            .sum();
    }

    public void initializeItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cart)) return false;
        Cart cart = (Cart) o;
        return id != null && id.equals(cart.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Cart{" +
                "id=" + id +
                ", sessionId='" + sessionId + '\'' +
                ", subtotal=" + subtotal +
                ", itemCount=" + items.size() +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
