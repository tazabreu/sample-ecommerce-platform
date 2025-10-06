package com.ecommerce.customer.service;

import com.ecommerce.customer.dto.CreateCategoryRequest;
import com.ecommerce.customer.dto.CreateProductRequest;
import com.ecommerce.customer.dto.UpdateCategoryRequest;
import com.ecommerce.customer.dto.UpdateProductRequest;
import com.ecommerce.customer.exception.DuplicateResourceException;
import com.ecommerce.customer.exception.ResourceNotFoundException;
import com.ecommerce.customer.mapper.CategoryMapper;
import com.ecommerce.customer.mapper.ProductMapper;
import com.ecommerce.customer.model.Category;
import com.ecommerce.customer.model.Product;
import com.ecommerce.customer.repository.CategoryRepository;
import com.ecommerce.customer.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service for managing product catalog (categories and products).
 * Handles CRUD operations for categories and products with proper validation.
 */
@Service
@Transactional
public class CatalogService {

    private static final Logger logger = LoggerFactory.getLogger(CatalogService.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    public CatalogService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            CategoryMapper categoryMapper,
            ProductMapper productMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
    }

    // ==================== Category Operations ====================

    /**
     * Create a new category.
     *
     * @param request the create request
     * @return the created category
     */
    public Category createCategory(CreateCategoryRequest request) {
        logger.info("Creating category: {}", request.name());
        
        Category category = categoryMapper.toEntity(request);
        category.setId(UUID.randomUUID());
        Category savedCategory = categoryRepository.save(category);
        
        logger.info("Created category with id: {}", savedCategory.getId());
        return savedCategory;
    }

    /**
     * Update an existing category.
     *
     * @param categoryId the category ID
     * @param request the update request
     * @return the updated category
     * @throws ResourceNotFoundException if category not found
     */
    public Category updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        logger.info("Updating category: {}", categoryId);
        
        Category category = getCategoryById(categoryId);
        categoryMapper.updateEntityFromDto(request, category);
        Category updatedCategory = categoryRepository.save(category);
        
        logger.info("Updated category: {}", categoryId);
        return updatedCategory;
    }

    /**
     * Delete a category.
     * Only allows deletion if no products are associated with the category.
     *
     * @param categoryId the category ID
     * @throws ResourceNotFoundException if category not found
     * @throws IllegalStateException if category has associated products
     */
    public void deleteCategory(UUID categoryId) {
        logger.info("Deleting category: {}", categoryId);
        
        Category category = getCategoryById(categoryId);
        
        // Check if category has products
        if (productRepository.existsByCategoryId(categoryId)) {
            throw new IllegalStateException(
                    "Cannot delete category with associated products. Category ID: " + categoryId
            );
        }
        
        categoryRepository.delete(category);
        logger.info("Deleted category: {}", categoryId);
    }

    /**
     * Get all categories.
     *
     * @return list of all categories
     */
    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        logger.debug("Listing all categories");
        return StreamSupport.stream(categoryRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Get category by ID.
     *
     * @param categoryId the category ID
     * @return the category
     * @throws ResourceNotFoundException if category not found
     */
    @Transactional(readOnly = true)
    public Category getCategoryById(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    // ==================== Product Operations ====================

    /**
     * Create a new product.
     *
     * @param request the create request
     * @return the created product
     * @throws ResourceNotFoundException if category not found
     * @throws DuplicateResourceException if SKU already exists
     */
    public Product createProduct(CreateProductRequest request) {
        logger.info("Creating product with SKU: {}", request.sku());
        
        // Validate category exists without loading aggregate
        validateCategoryExists(request.categoryId());
        
        // Check SKU uniqueness
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product", "SKU", request.sku());
        }
        
        Product product = productMapper.toEntity(request);
        product.setCategoryId(request.categoryId());
        Product savedProduct = productRepository.save(product);
        
        logger.info("Created product with id: {}", savedProduct.getId());
        return savedProduct;
    }

    /**
     * Update an existing product.
     *
     * @param productId the product ID
     * @param request the update request
     * @return the updated product
     * @throws ResourceNotFoundException if product or category not found
     */
    public Product updateProduct(UUID productId, UpdateProductRequest request) {
        logger.info("Updating product: {}", productId);
        
        Product product = getProductById(productId);
        
        // If category is being updated, validate it exists
        if (request.categoryId() != null && !request.categoryId().equals(product.getCategoryId())) {
            validateCategoryExists(request.categoryId());
            product.setCategoryId(request.categoryId());
        }
        
        productMapper.updateEntityFromDto(request, product);
        Product updatedProduct = productRepository.save(product);
        
        logger.info("Updated product: {}", productId);
        return updatedProduct;
    }

    /**
     * Delete (discontinue) a product by setting isActive to false.
     * Soft delete to preserve historical data.
     *
     * @param productId the product ID
     * @throws ResourceNotFoundException if product not found
     */
    public void deleteProduct(UUID productId) {
        logger.info("Deleting (soft) product: {}", productId);
        
        Product product = getProductById(productId);
        product.setIsActive(false);
        productRepository.save(product);
        
        logger.info("Deleted (soft) product: {}", productId);
    }

    /**
     * List products with optional filtering and pagination.
     *
     * @param categoryId optional category filter
     * @param isActive optional active status filter
     * @param pageable pagination parameters
     * @return page of products
     */
    @Transactional(readOnly = true)
    public Page<Product> listProducts(UUID categoryId, Boolean isActive, Pageable pageable) {
        logger.debug("Listing products - categoryId: {}, isActive: {}, page: {}", 
                categoryId, isActive, pageable.getPageNumber());
        
        if (categoryId != null && isActive != null) {
            return productRepository.findByCategoryIdAndIsActive(categoryId, isActive, pageable);
        } else if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId, pageable);
        } else if (isActive != null) {
            return productRepository.findByIsActive(isActive, pageable);
        } else {
            return productRepository.findAll(pageable);
        }
    }

    /**
     * Get product by ID.
     *
     * @param productId the product ID
     * @return the product
     * @throws ResourceNotFoundException if product not found
     */
    @Transactional(readOnly = true)
    public Product getProductById(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private void validateCategoryExists(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
    }
}
