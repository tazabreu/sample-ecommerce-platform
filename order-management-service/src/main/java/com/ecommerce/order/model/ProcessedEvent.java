package com.ecommerce.order.model;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * ProcessedEvent entity for tracking processed Kafka events (idempotency).
 * 
 * <p>This entity ensures that Kafka events are processed exactly once by recording
 * the event ID after successful processing. Duplicate event deliveries (at-least-once
 * semantics) are detected and skipped.</p>
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>Event ID as primary key (unique constraint)</li>
 *   <li>Event type tracking for debugging and analytics</li>
 *   <li>Processed timestamp for audit trail</li>
 *   <li>Automatic cleanup via scheduled jobs (retention policy)</li>
 * </ul>
 * 
 * <p>Usage:</p>
 * <pre>
 * // Before processing event
 * if (processedEventRepository.existsByEventId(event.getEventId())) {
 *     log.info("Event already processed, skipping: {}", event.getEventId());
 *     return;
 * }
 * 
 * // Process event...
 * 
 * // After successful processing
 * processedEventRepository.save(new ProcessedEvent(event.getEventId(), event.getEventType()));
 * </pre>
 */
@Table("processed_events")
public class ProcessedEvent implements StatefulPersistable<UUID> {

    @Id
    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @NotNull(message = "Event type is required")
    private String eventType;

    private Instant processedAt;

    @Transient
    private boolean isNew = true;

    // Constructors

    /**
     * Default constructor required by JPA.
     */
    protected ProcessedEvent() {
    }

    /**
     * Constructor for creating a new processed event record.
     *
     * @param eventId   the unique event identifier
     * @param eventType the event type (e.g., "ORDER_CREATED", "PAYMENT_COMPLETED")
     */
    public ProcessedEvent(UUID eventId, String eventType) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Event type cannot be null or blank");
        }

        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    // Getters

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public void markPersisted() {
        this.isNew = false;
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessedEvent)) return false;
        ProcessedEvent that = (ProcessedEvent) o;
        return eventId != null && eventId.equals(that.eventId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ProcessedEvent{" +
                "eventId=" + eventId +
                ", eventType='" + eventType + '\'' +
                ", processedAt=" + processedAt +
                '}';
    }
}
