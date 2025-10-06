package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.OrderCreatedOutbox;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for order created outbox operations.
 */
@Repository
public interface OrderCreatedOutboxRepository extends CrudRepository<OrderCreatedOutbox, UUID> {

    /**
     * Custom insert with JSONB cast for payload field.
     */
    @Modifying
    @Query("INSERT INTO order_created_outbox (id, aggregate_id, aggregate_type, event_type, payload, created_at, status, retry_count) " +
           "VALUES (:id, :aggregateId, :aggregateType, :eventType, CAST(:payload AS JSONB), :createdAt, CAST(:status AS VARCHAR), :retryCount)")
    void insertOutbox(@Param("id") UUID id,
                     @Param("aggregateId") UUID aggregateId,
                     @Param("aggregateType") String aggregateType,
                     @Param("eventType") String eventType,
                     @Param("payload") String payload,
                     @Param("createdAt") Instant createdAt,
                     @Param("status") String status,
                     @Param("retryCount") Integer retryCount);

    /**
     * Custom update with JSONB cast for payload field.
     */
    @Modifying
    @Query("UPDATE order_created_outbox SET aggregate_id = :aggregateId, aggregate_type = :aggregateType, " +
           "event_type = :eventType, payload = CAST(:payload AS JSONB), created_at = :createdAt, " +
           "published_at = :publishedAt, status = CAST(:status AS VARCHAR), retry_count = :retryCount, " +
           "error_message = :errorMessage WHERE id = :id")
    void updateOutbox(@Param("id") UUID id,
                     @Param("aggregateId") UUID aggregateId,
                     @Param("aggregateType") String aggregateType,
                     @Param("eventType") String eventType,
                     @Param("payload") String payload,
                     @Param("createdAt") Instant createdAt,
                     @Param("publishedAt") Instant publishedAt,
                     @Param("status") String status,
                     @Param("retryCount") Integer retryCount,
                     @Param("errorMessage") String errorMessage);


    /**
     * Find pending events ordered by creation time (FIFO).
     * Limits results to prevent overwhelming the publisher.
     *
     * @param limit maximum number of events to return
     * @return list of pending outbox events
     */
    @Query("SELECT * FROM order_created_outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit")
    List<OrderCreatedOutbox> findPendingEvents(@Param("limit") int limit);

    /**
     * Find events that have been pending for too long (stuck events).
     * Useful for alerting or manual intervention.
     *
     * @param olderThan timestamp threshold
     * @return list of stuck events
     */
    @Query("SELECT * FROM order_created_outbox WHERE status = 'PENDING' AND created_at < :olderThan")
    List<OrderCreatedOutbox> findStuckEvents(@Param("olderThan") Instant olderThan);

    /**
     * Count pending events.
     *
     * @return number of pending events
     */
    @Query("SELECT COUNT(*) FROM order_created_outbox WHERE status = 'PENDING'")
    long countPending();
}
