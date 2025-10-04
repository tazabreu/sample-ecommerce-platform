package com.ecommerce.customer.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
@Entity
@Table(name = "categories", indexes = {
    @Index(name = "idx_category_name", columnList = "name", unique = true)
})
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Category name is required")
    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    private String description;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Product> products = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public List<Product> getProducts() {
        return products;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Business methods

    /**
     * Checks if this category has any associated products.
     *
     * @return true if the category has products, false otherwise
     */
    public boolean hasProducts() {
        return products != null && !products.isEmpty();
    }

    /**
     * Returns the count of products in this category.
     *
     * @return the number of products
     */
    public int getProductCount() {
        return products != null ? products.size() : 0;
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
                ", productCount=" + getProductCount() +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

