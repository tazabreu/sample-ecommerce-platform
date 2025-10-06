package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.CheckoutIdempotency;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for checkout idempotency operations.
 */
@Repository
public interface CheckoutIdempotencyRepository extends CrudRepository<CheckoutIdempotency, String> {

    /**
     * Find idempotency record by key that hasn't expired.
     *
     * @param idempotencyKey the idempotency key
     * @param now current timestamp
     * @return the idempotency record, if found and not expired
     */
    @Query("SELECT * FROM checkout_idempotency WHERE idempotency_key = :key AND expires_at > :now")
    Optional<CheckoutIdempotency> findByIdempotencyKeyAndNotExpired(
            @Param("key") String idempotencyKey,
            @Param("now") Instant now
    );

    /**
     * Delete expired idempotency records.
     * Should be called periodically to clean up stale data.
     *
     * @param now current timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM checkout_idempotency WHERE expires_at <= :now")
    int deleteExpiredRecords(@Param("now") Instant now);
}
