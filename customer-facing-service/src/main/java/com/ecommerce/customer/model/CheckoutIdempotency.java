package com.ecommerce.customer.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

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
@Table("checkout_idempotency")
public class CheckoutIdempotency implements StatefulPersistable<String> {

    @Id
    private String idempotencyKey;

    private String requestFingerprint;

    private Integer responseStatus;

    private String responseBody;

    private Instant createdAt;

    private Instant expiresAt;

    @Transient
    private boolean isNew = true;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        if (expiresAt == null && createdAt != null) {
            this.expiresAt = createdAt.plusSeconds(24 * 60 * 60L);
        }
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String getId() {
        return idempotencyKey;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public void markPersisted() {
        this.isNew = false;
    }
}
