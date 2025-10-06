package com.ecommerce.customer.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox entity for OrderCreated events.
 * Implements transactional outbox pattern to ensure at-least-once delivery.
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>Created with status=PENDING in checkout transaction</li>
 *   <li>Background publisher polls PENDING events</li>
 *   <li>Published to Kafka → status=PUBLISHED, published_at set</li>
 *   <li>If publish fails → retry_count++, status remains PENDING</li>
 *   <li>If max retries exceeded → status=FAILED, sent to DLQ</li>
 * </ol>
 */
@Table("order_created_outbox")
public class OrderCreatedOutbox {

    public enum Status {
        PENDING, PUBLISHED, FAILED
    }

    @Id
    private UUID id;

    private UUID aggregateId;

    private String aggregateType = "ORDER";

    private String eventType = "OrderCreatedEvent";

    private String payload;

    private Instant createdAt;

    private Instant publishedAt;

    private Status status = Status.PENDING;

    private Integer retryCount = 0;

    private String errorMessage;

    // Constructors
    public OrderCreatedOutbox() {
    }

    public OrderCreatedOutbox(UUID aggregateId, String payload) {
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }


    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void markAsPublished() {
        this.status = Status.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markAsFailed(String error) {
        this.status = Status.FAILED;
        this.errorMessage = error;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
