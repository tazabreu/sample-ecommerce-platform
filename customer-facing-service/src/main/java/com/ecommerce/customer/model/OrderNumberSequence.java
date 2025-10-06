package com.ecommerce.customer.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

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
@Table("order_number_sequence")
public class OrderNumberSequence implements Auditable, StatefulPersistable<String> {

    @Id
    private String dateKey;

    private Integer lastSequence = 0;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

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

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String getId() {
        return dateKey;
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
