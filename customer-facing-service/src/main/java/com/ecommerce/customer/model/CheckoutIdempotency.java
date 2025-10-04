package com.ecommerce.customer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for storing checkout idempotency records.
 * Enables idempotent checkout operations to prevent duplicate orders.
 *
 * <p>Idempotency Pattern:</p>
 * <ul>
 *   <li>Client provides Idempotency-Key header (UUID recommended)</li>
 *   <li>Store key → fingerprint → response for 24h</li>
 *   <li>Replays with same key + fingerprint return cached response</li>
 *   <li>Replays with same key + different fingerprint return 422 Unprocessable Entity</li>
 * </ul>
 */
@Entity
@Table(name = "checkout_idempotency")
public class CheckoutIdempotency {

    @Id
    @Column(name = "idempotency_key", length = 255, nullable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64, nullable = false)
    private String requestFingerprint;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(24);
        }
    }

    // Constructors
    public CheckoutIdempotency() {
    }

    public CheckoutIdempotency(String idempotencyKey, String requestFingerprint, Integer responseStatus, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    // Getters and Setters
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
