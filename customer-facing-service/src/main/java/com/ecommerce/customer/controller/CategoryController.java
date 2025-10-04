package com.ecommerce.customer.controller;

import com.ecommerce.customer.dto.CategoryDto;
import com.ecommerce.customer.dto.CreateCategoryRequest;
import com.ecommerce.customer.dto.UpdateCategoryRequest;
import com.ecommerce.customer.mapper.CategoryMapper;
import com.ecommerce.customer.service.CatalogService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing product categories.
 * 
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/v1/categories - List all categories (public)</li>
 *   <li>POST /api/v1/categories - Create category (manager only)</li>
 *   <li>GET /api/v1/categories/{id} - Get category by ID (public)</li>
 *   <li>PUT /api/v1/categories/{id} - Update category (manager only)</li>
 *   <li>DELETE /api/v1/categories/{id} - Delete category (manager only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);

    private final CatalogService catalogService;
    private final CategoryMapper categoryMapper;

    public CategoryController(CatalogService catalogService, CategoryMapper categoryMapper) {
        this.catalogService = catalogService;
        this.categoryMapper = categoryMapper;
    }

    /**
     * List all categories.
     * Public endpoint - no authentication required.
     *
     * @return list of all categories
     */
    @GetMapping
    public ResponseEntity<List<CategoryDto>> listCategories() {
        logger.info("Listing all categories");
        List<CategoryDto> categories = categoryMapper.toDtoList(catalogService.listCategories());
        logger.info("Found {} categories", categories.size());
        return ResponseEntity.ok(categories);
    }

    /**
     * Get category by ID.
     * Public endpoint - no authentication required.
     *
     * @param id the category ID
     * @return the category
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getCategoryById(@PathVariable UUID id) {
        logger.info("Getting category by ID: {}", id);
        CategoryDto category = categoryMapper.toDto(catalogService.getCategoryById(id));
        return ResponseEntity.ok(category);
    }

    /**
     * Create a new category.
     * Manager role required.
     *
     * @param request the category creation request
     * @return the created category with 201 Created status
     */
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        logger.info("Creating category: {}", request.name());
        CategoryDto category = categoryMapper.toDto(catalogService.createCategory(request));
        logger.info("Category created successfully - id: {}, name: {}", category.id(), category.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    /**
     * Update an existing category.
     * Manager role required.
     *
     * @param id the category ID
     * @param request the category update request
     * @return the updated category
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        logger.info("Updating category: {}", id);
        CategoryDto category = categoryMapper.toDto(catalogService.updateCategory(id, request));
        logger.info("Category updated successfully - id: {}, name: {}", category.id(), category.name());
        return ResponseEntity.ok(category);
    }

    /**
     * Delete a category.
     * Manager role required.
     * Will fail if the category has associated products.
     *
     * @param id the category ID
     * @return 204 No Content on successful deletion
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        logger.info("Deleting category: {}", id);
        catalogService.deleteCategory(id);
        logger.info("Category deleted successfully: {}", id);
        return ResponseEntity.noContent().build();
    }
}


