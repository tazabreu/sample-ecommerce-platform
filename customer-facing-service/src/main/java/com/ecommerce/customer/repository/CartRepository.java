package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Cart entity.
 * 
 * <p>Provides standard CRUD operations plus custom queries for cart management and cleanup.</p>
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    /**
     * Finds a cart by its session ID.
     *
     * @param sessionId the session identifier
     * @return an Optional containing the cart, or empty if not found
     */
    Optional<Cart> findBySessionId(String sessionId);

    /**
     * Finds a cart by session ID eagerly loading items and associated products.
     *
     * @param sessionId the session identifier
     * @return an Optional containing the cart with items, or empty if not found
     */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findWithItemsBySessionId(String sessionId);

    /**
     * Checks if a cart with the given session ID exists.
     *
     * @param sessionId the session identifier
     * @return true if a cart with the session ID exists, false otherwise
     */
    boolean existsBySessionId(String sessionId);

    /**
     * Deletes a cart by its session ID.
     *
     * @param sessionId the session identifier
     * @return the number of carts deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM Cart c WHERE c.sessionId = :sessionId")
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * Deletes all carts that have expired before the given timestamp.
     * This is used for cleanup jobs to remove abandoned carts.
     *
     * @param expirationTime the timestamp to compare against
     * @return the number of carts deleted
     */
    @Modifying
    @Query("DELETE FROM Cart c WHERE c.expiresAt < :expirationTime")
    int deleteByExpiresAtBefore(@Param("expirationTime") Instant expirationTime);

    /**
     * Finds all carts that have expired before the given timestamp.
     * Useful for batch processing before deletion.
     *
     * @param expirationTime the timestamp to compare against
     * @return a list of expired carts
     */
    @Query("SELECT c FROM Cart c WHERE c.expiresAt < :expirationTime")
    java.util.List<Cart> findExpiredCarts(@Param("expirationTime") Instant expirationTime);

    /**
     * Counts the number of active (non-expired) carts.
     *
     * @param currentTime the current timestamp
     * @return the count of active carts
     */
    @Query("SELECT COUNT(c) FROM Cart c WHERE c.expiresAt > :currentTime")
    long countActiveCarts(@Param("currentTime") Instant currentTime);
}

