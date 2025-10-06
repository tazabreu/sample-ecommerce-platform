package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.Cart;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
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
public interface CartRepository extends CrudRepository<Cart, UUID> {

    /**
     * Finds a cart by its session ID.
     *
     * @param sessionId the session identifier
     * @return an Optional containing the cart, or empty if not found
     */
    Optional<Cart> findBySessionId(String sessionId);

    @Query("SELECT * FROM carts WHERE session_id = :sessionId")
    Optional<Cart> findWithItemsBySessionId(@Param("sessionId") String sessionId);

    /**
     * Finds a cart by session ID eagerly loading items and associated products.
     *
     * @param sessionId the session identifier
     * @return an Optional containing the cart with items, or empty if not found
     */
    boolean existsBySessionId(String sessionId);

    /**
     * Deletes a cart by its session ID.
     *
     * @param sessionId the session identifier
     * @return the number of carts deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM carts WHERE session_id = :sessionId")
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * Deletes all carts that have expired before the given timestamp.
     * This is used for cleanup jobs to remove abandoned carts.
     *
     * @param expirationTime the timestamp to compare against
     * @return the number of carts deleted
     */
    @Modifying
    @Query("DELETE FROM carts WHERE expires_at < :expirationTime")
    int deleteByExpiresAtBefore(@Param("expirationTime") Instant expirationTime);

    /**
     * Finds all carts that have expired before the given timestamp.
     * Useful for batch processing before deletion.
     *
     * @param expirationTime the timestamp to compare against
     * @return a list of expired carts
     */
    @Query("SELECT * FROM carts WHERE expires_at < :expirationTime")
    java.util.List<Cart> findExpiredCarts(@Param("expirationTime") Instant expirationTime);

    /**
     * Counts the number of active (non-expired) carts.
     *
     * @param currentTime the current timestamp
     * @return the count of active carts
     */
    @Query("SELECT COUNT(*) FROM carts WHERE expires_at > :currentTime")
    long countActiveCarts(@Param("currentTime") Instant currentTime);
}
