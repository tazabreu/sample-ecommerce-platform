package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.Category;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Category entity.
 * 
 * <p>Provides standard CRUD operations plus custom queries for category management.</p>
 */
@Repository
public interface CategoryRepository extends CrudRepository<Category, UUID> {

    /**
     * Finds a category by its name.
     *
     * @param name the category name (case-sensitive)
     * @return an Optional containing the category, or empty if not found
     */
    Optional<Category> findByName(String name);

    /**
     * Checks if a category with the given name exists.
     *
     * @param name the category name (case-sensitive)
     * @return true if a category with the name exists, false otherwise
     */
    boolean existsByName(String name);
}
