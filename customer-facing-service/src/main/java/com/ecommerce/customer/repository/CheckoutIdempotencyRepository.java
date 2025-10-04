package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.CheckoutIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for checkout idempotency operations.
 */
@Repository
public interface CheckoutIdempotencyRepository extends JpaRepository<CheckoutIdempotency, String> {

    /**
     * Find idempotency record by key that hasn't expired.
     *
     * @param idempotencyKey the idempotency key
     * @param now current timestamp
     * @return the idempotency record, if found and not expired
     */
    @Query("SELECT ci FROM CheckoutIdempotency ci WHERE ci.idempotencyKey = :key AND ci.expiresAt > :now")
    Optional<CheckoutIdempotency> findByIdempotencyKeyAndNotExpired(
            @Param("key") String idempotencyKey,
            @Param("now") LocalDateTime now
    );

    /**
     * Delete expired idempotency records.
     * Should be called periodically to clean up stale data.
     *
     * @param now current timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM CheckoutIdempotency ci WHERE ci.expiresAt <= :now")
    int deleteExpiredRecords(@Param("now") LocalDateTime now);
}
