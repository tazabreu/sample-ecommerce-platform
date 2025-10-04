package com.ecommerce.customer.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
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
@Entity
@Table(name = "order_created_outbox")
public class OrderCreatedOutbox {

    public enum Status {
        PENDING, PUBLISHED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", length = 50, nullable = false)
    private String aggregateType = "ORDER";

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType = "OrderCreatedEvent";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Constructors
    public OrderCreatedOutbox() {
    }

    public OrderCreatedOutbox(UUID aggregateId, String payload) {
        this.aggregateId = aggregateId;
        this.payload = payload;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
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
        this.publishedAt = LocalDateTime.now();
    }

    public void markAsFailed(String error) {
        this.status = Status.FAILED;
        this.errorMessage = error;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
