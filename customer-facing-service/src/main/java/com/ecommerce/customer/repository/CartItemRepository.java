package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CartItem entity.
 * 
 * <p>Provides standard CRUD operations plus custom queries for cart item management.</p>
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    /**
     * Finds all cart items for a specific cart.
     *
     * @param cartId the cart ID
     * @return a list of cart items
     */
    List<CartItem> findByCartId(UUID cartId);

    /**
     * Finds a cart item by cart ID and product ID.
     * Useful for checking if a product is already in the cart.
     *
     * @param cartId    the cart ID
     * @param productId the product ID
     * @return an Optional containing the cart item, or empty if not found
     */
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    /**
     * Checks if a cart item exists for a specific cart and product.
     *
     * @param cartId    the cart ID
     * @param productId the product ID
     * @return true if a cart item exists, false otherwise
     */
    boolean existsByCartIdAndProductId(UUID cartId, UUID productId);

    /**
     * Counts the number of items in a specific cart.
     *
     * @param cartId the cart ID
     * @return the count of cart items
     */
    long countByCartId(UUID cartId);

    /**
     * Finds all cart items for a specific product across all carts.
     * Useful for analytics (e.g., product popularity in carts).
     *
     * @param productId the product ID
     * @return a list of cart items
     */
    List<CartItem> findByProductId(UUID productId);

    /**
     * Calculates the total quantity of a specific product across all carts.
     * Useful for inventory reservation calculations.
     *
     * @param productId the product ID
     * @return the sum of quantities
     */
    @Query("SELECT COALESCE(SUM(ci.quantity), 0) FROM CartItem ci WHERE ci.product.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

