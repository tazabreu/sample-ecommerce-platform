package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.OrderCreatedOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for order created outbox operations.
 */
@Repository
public interface OrderCreatedOutboxRepository extends JpaRepository<OrderCreatedOutbox, UUID> {

    /**
     * Find pending events ordered by creation time (FIFO).
     * Limits results to prevent overwhelming the publisher.
     *
     * @param limit maximum number of events to return
     * @return list of pending outbox events
     */
    @Query("SELECT o FROM OrderCreatedOutbox o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OrderCreatedOutbox> findPendingEvents(@Param("limit") int limit);

    /**
     * Find events that have been pending for too long (stuck events).
     * Useful for alerting or manual intervention.
     *
     * @param olderThan timestamp threshold
     * @return list of stuck events
     */
    @Query("SELECT o FROM OrderCreatedOutbox o WHERE o.status = 'PENDING' AND o.createdAt < :olderThan")
    List<OrderCreatedOutbox> findStuckEvents(@Param("olderThan") LocalDateTime olderThan);

    /**
     * Count pending events.
     *
     * @return number of pending events
     */
    @Query("SELECT COUNT(o) FROM OrderCreatedOutbox o WHERE o.status = 'PENDING'")
    long countPending();
}
