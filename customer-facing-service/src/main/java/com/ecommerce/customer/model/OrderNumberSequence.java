package com.ecommerce.customer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for storing daily order number sequences.
 * Enables DB-backed, instance-safe order number generation.
 *
 * <p>Format: ORD-YYYYMMDD-###</p>
 * <ul>
 *   <li>date_key: YYYYMMDD (e.g., 20250104)</li>
 *   <li>last_sequence: Incremental counter that resets daily</li>
 * </ul>
 */
@Entity
@Table(name = "order_number_sequence")
public class OrderNumberSequence {

    @Id
    @Column(name = "date_key", length = 8, nullable = false)
    private String dateKey;

    @Column(name = "last_sequence", nullable = false)
    private Integer lastSequence = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public OrderNumberSequence() {
    }

    public OrderNumberSequence(String dateKey, Integer lastSequence) {
        this.dateKey = dateKey;
        this.lastSequence = lastSequence;
    }

    // Getters and Setters
    public String getDateKey() {
        return dateKey;
    }

    public void setDateKey(String dateKey) {
        this.dateKey = dateKey;
    }

    public Integer getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(Integer lastSequence) {
        this.lastSequence = lastSequence;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
