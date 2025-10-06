package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
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
public interface ProductRepository extends PagingAndSortingRepository<Product, UUID> {

    /**
     * Find product by ID.
     */
    Optional<Product> findById(UUID id);

    /**
     * Persist product explicitly (required in JDBC).
     */
    <S extends Product> S save(S entity);

    /**
     * Fetch all products matching the provided IDs.
     */
    Iterable<Product> findAllById(Iterable<UUID> ids);

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
     *
     * @param pageable pagination information
     * @return a page of active products
     */
    Page<Product> findByIsActiveTrue(Pageable pageable);

    /**
     * Finds all products in a specific category with stock availability.
     *
     * @param categoryId the category ID
     * @return a list of products with inventory > 0
     */
    @Query("SELECT * FROM products WHERE category_id = :categoryId AND inventory_quantity > 0 AND is_active = true")
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
    @Query("SELECT * FROM products WHERE id = :id FOR UPDATE")
    Optional<Product> findByIdWithLock(@Param("id") UUID id);
}
