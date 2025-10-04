package com.ecommerce.customer.controller;

import com.ecommerce.customer.dto.CreateProductRequest;
import com.ecommerce.customer.dto.ProductDto;
import com.ecommerce.customer.model.Product;
import com.ecommerce.customer.dto.ProductPageDto;
import com.ecommerce.customer.dto.UpdateProductRequest;
import com.ecommerce.customer.mapper.ProductMapper;
import com.ecommerce.customer.service.CatalogService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing products.
 * 
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/v1/products - List products with pagination and filters (public)</li>
 *   <li>POST /api/v1/products - Create product (manager only)</li>
 *   <li>GET /api/v1/products/{id} - Get product by ID (public)</li>
 *   <li>PUT /api/v1/products/{id} - Update product (manager only)</li>
 *   <li>DELETE /api/v1/products/{id} - Delete product (soft delete, manager only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final CatalogService catalogService;
    private final ProductMapper productMapper;

    public ProductController(CatalogService catalogService, ProductMapper productMapper) {
        this.catalogService = catalogService;
        this.productMapper = productMapper;
    }

    /**
     * List products with pagination and optional filters.
     * Public endpoint - no authentication required.
     *
     * @param categoryId optional category filter
     * @param isActive optional active status filter (defaults to true for public)
     * @param page page number (0-indexed)
     * @param size page size
     * @param sortBy field to sort by (default: name)
     * @param sortDir sort direction (asc or desc)
     * @return paginated list of products
     */
    @GetMapping
    public ResponseEntity<ProductPageDto> listProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false, defaultValue = "true") Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        logger.info("Listing products - categoryId: {}, isActive: {}, page: {}, size: {}", 
                categoryId, isActive, page, size);

        // Create pageable with sorting
        Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? Sort.by(sortBy).descending() 
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // Get products
        Page<Product> productsPage = catalogService.listProducts(categoryId, isActive, pageable);

        // Convert to DTOs and ProductPageDto
        List<ProductDto> productDtos = productMapper.toDtoList(productsPage.getContent());
        ProductPageDto response = new ProductPageDto(
                productDtos,
                productsPage.getTotalElements(),
                productsPage.getTotalPages(),
                productsPage.getSize(),
                productsPage.getNumber()
        );

        logger.info("Found {} products (page {} of {})", 
                response.content().size(), response.number() + 1, response.totalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * Get product by ID.
     * Public endpoint - no authentication required.
     *
     * @param id the product ID
     * @return the product
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable UUID id) {
        logger.info("Getting product by ID: {}", id);
        ProductDto product = productMapper.toDto(catalogService.getProductById(id));
        return ResponseEntity.ok(product);
    }

    /**
     * Create a new product.
     * Manager role required.
     *
     * @param request the product creation request
     * @return the created product with 201 Created status
     */
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody CreateProductRequest request) {
        logger.info("Creating product - sku: {}, name: {}", request.sku(), request.name());
        ProductDto product = productMapper.toDto(catalogService.createProduct(request));
        logger.info("Product created successfully - id: {}, sku: {}", product.id(), product.sku());
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    /**
     * Update an existing product.
     * Manager role required.
     *
     * @param id the product ID
     * @param request the product update request
     * @return the updated product
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        logger.info("Updating product: {}", id);
        ProductDto product = productMapper.toDto(catalogService.updateProduct(id, request));
        logger.info("Product updated successfully - id: {}, sku: {}", product.id(), product.sku());
        return ResponseEntity.ok(product);
    }

    /**
     * Delete a product (soft delete - sets isActive to false).
     * Manager role required.
     *
     * @param id the product ID
     * @return 204 No Content on successful deletion
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        logger.info("Deleting product: {}", id);
        catalogService.deleteProduct(id);
        logger.info("Product deleted successfully (soft delete): {}", id);
        return ResponseEntity.noContent().build();
    }
}


