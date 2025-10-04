package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Product entity.
 * 
 * <p>Provides standard CRUD operations plus custom queries for product management and filtering.</p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Finds a product by its SKU.
     *
     * @param sku the product SKU (case-sensitive)
     * @return an Optional containing the product, or empty if not found
     */
    Optional<Product> findBySku(String sku);

    /**
     * Checks if a product with the given SKU exists.
     *
     * @param sku the product SKU (case-sensitive)
     * @return true if a product with the SKU exists, false otherwise
     */
    boolean existsBySku(String sku);

    /**
     * Finds all products in a specific category.
     *
     * @param categoryId the category ID
     * @param pageable   pagination information
     * @return a page of products in the category
     */
    Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);

    /**
     * Finds all active products in a specific category.
     *
     * @param categoryId the category ID
     * @param isActive   the active status filter
     * @param pageable   pagination information
     * @return a page of active products in the category
     */
    Page<Product> findByCategoryIdAndIsActive(UUID categoryId, Boolean isActive, Pageable pageable);

    /**
     * Finds all products filtered by active status.
     *
     * @param isActive the active status filter
     * @param pageable pagination information
     * @return a page of products matching the filter
     */
    Page<Product> findByIsActive(Boolean isActive, Pageable pageable);

    /**
     * Finds all active products with pagination.
     * This is an optimized query that can leverage database indexes.
     *
     * @param pageable pagination information
     * @return a page of active products
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true")
    Page<Product> findAllActiveProducts(Pageable pageable);

    /**
     * Finds all products in a specific category with stock availability.
     *
     * @param categoryId the category ID
     * @return a list of products with inventory > 0
     */
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.inventoryQuantity > 0 AND p.isActive = true")
    List<Product> findInStockProductsByCategory(@Param("categoryId") UUID categoryId);

    /**
     * Counts the number of products in a specific category.
     *
     * @param categoryId the category ID
     * @return the count of products
     */
    long countByCategoryId(UUID categoryId);

    /**
     * Checks if any products exist for a given category.
     *
     * @param categoryId the category ID
     * @return true if at least one product exists in the category, false otherwise
     */
    boolean existsByCategoryId(UUID categoryId);

    /**
     * Finds a product by ID with pessimistic write lock.
     * Used during checkout to prevent concurrent inventory updates.
     *
     * @param id the product ID
     * @return an Optional containing the locked product, or empty if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") UUID id);
}

