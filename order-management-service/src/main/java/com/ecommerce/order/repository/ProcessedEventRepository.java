package com.ecommerce.order.repository;

import com.ecommerce.order.model.ProcessedEvent;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository interface for ProcessedEvent entity (idempotency tracking).
 * 
 * <p>This repository is critical for ensuring idempotent event processing in the
 * Kafka consumer. Each event ID is recorded once to prevent duplicate processing.</p>
 */
@Repository
public interface ProcessedEventRepository extends CrudRepository<ProcessedEvent, UUID> {

    /**
     * Checks if an event with the given ID has already been processed.
     * This is the primary idempotency check used by Kafka consumers.
     *
     * @param eventId the unique event identifier
     * @return true if the event has been processed, false otherwise
     */
    boolean existsByEventId(UUID eventId);

    /**
     * Deletes processed event records older than a specific timestamp.
     * This is used for cleanup/archival to prevent unbounded table growth.
     *
     * @param processedBefore the timestamp threshold
     * @return the number of records deleted
     */
    @Modifying
    @Query("DELETE FROM processed_events WHERE processed_at < :processedBefore")
    int deleteOldProcessedEvents(@Param("processedBefore") Instant processedBefore);

    /**
     * Counts the total number of processed events.
     * Useful for monitoring and capacity planning.
     *
     * @return the count of processed events
     */
    @Query("SELECT COUNT(*) FROM processed_events")
    long countTotalProcessedEvents();
}
