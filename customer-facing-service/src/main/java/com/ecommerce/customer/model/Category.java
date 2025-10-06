package com.ecommerce.customer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Category entity representing a product category in the e-commerce catalog.
 * Categories organize products into browsable groups (e.g., "Electronics", "Clothing").
 * 
 * <p>Validation Rules:</p>
 * <ul>
 *   <li>Name: Required, 1-100 characters, unique</li>
 *   <li>Description: Optional, max 1000 characters</li>
 * </ul>
 * 
 * <p>Relationships:</p>
 * <ul>
 *   <li>One-to-Many: A category can have multiple products</li>
 * </ul>
 */
@Table("categories")
public class Category implements Auditable, StatefulPersistable<UUID>, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private UUID id;

    @NotNull(message = "Category name is required")
    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private Instant createdAt;
    private Instant updatedAt;

    @Transient
    @JsonIgnore
    private boolean isNew = true;

    // Constructors

    /**
     * Default constructor required by JPA.
     */
    protected Category() {
    }

    /**
     * Constructor for creating a new category.
     *
     * @param name        the category name (required, unique)
     * @param description the category description (optional)
     */
    public Category(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public void markPersisted() {
        this.isNew = false;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category)) return false;
        Category category = (Category) o;
        return id != null && id.equals(category.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Category{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
