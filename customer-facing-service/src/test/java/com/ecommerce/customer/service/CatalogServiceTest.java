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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for CatalogService.
 * Tests business logic with mocked dependencies (no Spring context).
 * 
 * Coverage Target: >90% line, >85% branch
 * Total Tests: 26 (Category: 8, Product Creation: 6, Product Update/Delete: 6, Listing: 6)
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ProductMapper productMapper;

    private CatalogService catalogService;

    private UUID testCategoryId;
    private UUID testProductId;
    private Category testCategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(
                categoryRepository,
                productRepository,
                categoryMapper,
                productMapper
        );

        testCategoryId = UUID.randomUUID();
        testProductId = UUID.randomUUID();
        
        testCategory = new Category("Electronics", "Electronic devices");
        testCategory.setId(testCategoryId);
        
        testProduct = new Product(
                "LAPTOP-001",
                "Gaming Laptop",
                "High performance laptop",
                new BigDecimal("1299.99"),
                50,
                testCategoryId
        );
        testProduct.setId(testProductId);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ==================== Category Operations Tests (8 tests) ====================

    @Test
    void createCategory_success() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", "Electronic devices");
        Category categoryToSave = new Category("Electronics", "Electronic devices");
        
        when(categoryMapper.toEntity(request)).thenReturn(categoryToSave);
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        // Act
        Category result = catalogService.createCategory(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testCategoryId);
        assertThat(result.getName()).isEqualTo("Electronics");
        
        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getId()).isNotNull();
    }

    @Test
    void createCategory_withDescription_success() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Books", "Books and magazines");
        Category category = new Category("Books", "Books and magazines");
        Category savedCategory = new Category("Books", "Books and magazines");
        savedCategory.setId(UUID.randomUUID());
        
        when(categoryMapper.toEntity(request)).thenReturn(category);
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        // Act
        Category result = catalogService.createCategory(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Books");
        assertThat(result.getDescription()).isEqualTo("Books and magazines");
    }

    @Test
    void updateCategory_success() {
        // Arrange
        UpdateCategoryRequest request = new UpdateCategoryRequest("Updated Electronics", "Updated description");
        
        when(categoryRepository.findById(testCategoryId)).thenReturn(Optional.of(testCategory));
        doNothing().when(categoryMapper).updateEntityFromDto(request, testCategory);
        when(categoryRepository.save(testCategory)).thenReturn(testCategory);

        // Act
        Category result = catalogService.updateCategory(testCategoryId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(categoryMapper).updateEntityFromDto(request, testCategory);
        verify(categoryRepository).save(testCategory);
    }

    @Test
    void updateCategory_notFound_throwsException() {
        // Arrange
        UpdateCategoryRequest request = new UpdateCategoryRequest("Updated Name", null);
        
        when(categoryRepository.findById(testCategoryId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> catalogService.updateCategory(testCategoryId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining(testCategoryId.toString());
        
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteCategory_success() {
        // Arrange
        when(categoryRepository.findById(testCategoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsByCategoryId(testCategoryId)).thenReturn(false);
        doNothing().when(categoryRepository).delete(testCategory);

        // Act
        catalogService.deleteCategory(testCategoryId);

        // Assert
        verify(productRepository).existsByCategoryId(testCategoryId);
        verify(categoryRepository).delete(testCategory);
    }

    @Test
    void deleteCategory_withProducts_throwsException() {
        // Arrange
        when(categoryRepository.findById(testCategoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsByCategoryId(testCategoryId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> catalogService.deleteCategory(testCategoryId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete category with associated products")
                .hasMessageContaining(testCategoryId.toString());
        
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void getCategoryById_success() {
        // Arrange
        when(categoryRepository.findById(testCategoryId)).thenReturn(Optional.of(testCategory));

        // Act
        Category result = catalogService.getCategoryById(testCategoryId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testCategoryId);
        assertThat(result.getName()).isEqualTo("Electronics");
    }

    @Test
    void getCategoryById_notFound_throwsException() {
        // Arrange
        when(categoryRepository.findById(testCategoryId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> catalogService.getCategoryById(testCategoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining(testCategoryId.toString());
    }

    // ==================== Product Operations - Creation Tests (6 tests) ====================

    @Test
    void createProduct_success() {
        // Arrange
        CreateProductRequest request = new CreateProductRequest(
                "LAPTOP-001",
                "Gaming Laptop",
                "High performance laptop",
                new BigDecimal("1299.99"),
                50,
                testCategoryId
        );
        
        when(categoryRepository.existsById(testCategoryId)).thenReturn(true);
        when(productRepository.existsBySku("LAPTOP-001")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(testProduct);
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act
        Product result = catalogService.createProduct(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testProductId);
        assertThat(result.getSku()).isEqualTo("LAPTOP-001");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
        assertThat(result.getInventoryQuantity()).isEqualTo(50);
        
        verify(categoryRepository).existsById(testCategoryId);
        verify(productRepository).existsBySku("LAPTOP-001");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_duplicateSKU_throwsException() {
        // Arrange
        CreateProductRequest request = new CreateProductRequest(
                "LAPTOP-001",
                "Gaming Laptop",
                "Description",
                new BigDecimal("1299.99"),
                50,
                testCategoryId
        );
        
        when(categoryRepository.existsById(testCategoryId)).thenReturn(true);
        when(productRepository.existsBySku("LAPTOP-001")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining("SKU")
                .hasMessageContaining("LAPTOP-001");
        
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_categoryNotFound_throwsException() {
        // Arrange
        CreateProductRequest request = new CreateProductRequest(
                "LAPTOP-001",
                "Gaming Laptop",
                "Description",
                new BigDecimal("1299.99"),
                50,
                testCategoryId
        );
        
        when(categoryRepository.existsById(testCategoryId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining(testCategoryId.toString());
        
        verify(productRepository, never()).existsBySku(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_capturesPriceSnapshot() {
        // Arrange
        BigDecimal originalPrice = new BigDecimal("999.99");
        CreateProductRequest request = new CreateProductRequest(
                "PHONE-001",
                "Smartphone",
                "Latest model",
                originalPrice,
                100,
                testCategoryId
        );
        
        Product product = new Product("PHONE-001", "Smartphone", "Latest model", 
                originalPrice, 100, testCategoryId);
        product.setId(UUID.randomUUID());
        
        when(categoryRepository.existsById(testCategoryId)).thenReturn(true);
        when(productRepository.existsBySku("PHONE-001")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(any(Product.class))).thenReturn(product);

        // Act
        Product result = catalogService.createProduct(request);

        // Assert
        assertThat(result.getPrice()).isEqualByComparingTo(originalPrice);
    }

    @Test
    void createProduct_defaultsIsActiveToTrue() {
        // Arrange
        CreateProductRequest request = new CreateProductRequest(
                "TABLET-001",
                "Tablet",
                "Description",
                new BigDecimal("499.99"),
                30,
                testCategoryId
        );
        
        Product product = new Product("TABLET-001", "Tablet", "Description",
                new BigDecimal("499.99"), 30, testCategoryId);
        product.setId(UUID.randomUUID());
        
        when(categoryRepository.existsById(testCategoryId)).thenReturn(true);
        when(productRepository.existsBySku("TABLET-001")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(any(Product.class))).thenReturn(product);

        // Act
        Product result = catalogService.createProduct(request);

        // Assert
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void createProduct_validatesInventoryNonNegative() {
        // Arrange - Request with valid non-negative inventory
        CreateProductRequest request = new CreateProductRequest(
                "WATCH-001",
                "Smartwatch",
                "Description",
                new BigDecimal("299.99"),
                0, // Edge case: zero inventory is valid
                testCategoryId
        );
        
        Product product = new Product("WATCH-001", "Smartwatch", "Description",
                new BigDecimal("299.99"), 0, testCategoryId);
        product.setId(UUID.randomUUID());
        
        when(categoryRepository.existsById(testCategoryId)).thenReturn(true);
        when(productRepository.existsBySku("WATCH-001")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(any(Product.class))).thenReturn(product);

        // Act
        Product result = catalogService.createProduct(request);

        // Assert
        assertThat(result.getInventoryQuantity()).isEqualTo(0);
        verify(productRepository).save(any(Product.class));
    }

    // ==================== Product Operations - Update/Delete Tests (6 tests) ====================

    @Test
    void updateProduct_success() {
        // Arrange
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Laptop",
                "Updated description",
                new BigDecimal("1199.99"),
                45,
                null, // Not changing category
                true
        );
        
        when(productRepository.findById(testProductId)).thenReturn(Optional.of(testProduct));
        doNothing().when(productMapper).updateEntityFromDto(request, testProduct);
        when(productRepository.save(testProduct)).thenReturn(testProduct);

        // Act
        Product result = catalogService.updateProduct(testProductId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(productMapper).updateEntityFromDto(request, testProduct);
        verify(productRepository).save(testProduct);
        // Verify category validation was not called since categoryId is null
        verify(categoryRepository, never()).existsById(any());
    }

    @Test
    void updateProduct_notFound_throwsException() {
        // Arrange
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Name",
                null,
                null,
                null,
                null,
                null
        );
        
        when(productRepository.findById(testProductId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> catalogService.updateProduct(testProductId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining(testProductId.toString());
        
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_categoryNotFound_throwsException() {
        // Arrange
        UUID newCategoryId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Laptop",
                null,
                null,
                null,
                newCategoryId,
                null
        );
        
        when(productRepository.findById(testProductId)).thenReturn(Optional.of(testProduct));
        when(categoryRepository.existsById(newCategoryId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> catalogService.updateProduct(testProductId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining(newCategoryId.toString());
        
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteProduct_softDelete_success() {
        // Arrange
        when(productRepository.findById(testProductId)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(testProduct)).thenReturn(testProduct);

        // Act
        catalogService.deleteProduct(testProductId);

        // Assert
        verify(productRepository).save(testProduct);
        assertThat(testProduct.getIsActive()).isFalse();
    }

    @Test
    void deleteProduct_notFound_throwsException() {
        // Arrange
        when(productRepository.findById(testProductId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> catalogService.deleteProduct(testProductId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining(testProductId.toString());
        
        verify(productRepository, never()).save(any());
    }

    @Test
    void getProductById_success() {
        // Arrange
        when(productRepository.findById(testProductId)).thenReturn(Optional.of(testProduct));

        // Act
        Product result = catalogService.getProductById(testProductId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testProductId);
        assertThat(result.getSku()).isEqualTo("LAPTOP-001");
    }

    // ==================== Product Listing & Filtering Tests (6 tests) ====================

    @Test
    void listProducts_noFilters_returnsAll() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, products.size());
        
        when(productRepository.findAll(pageable)).thenReturn(productPage);

        // Act
        Page<Product> result = catalogService.listProducts(null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSku()).isEqualTo("LAPTOP-001");
        
        verify(productRepository).findAll(pageable);
        verify(productRepository, never()).findByCategoryId(any(), any());
        verify(productRepository, never()).findByIsActive(any(), any());
        verify(productRepository, never()).findByCategoryIdAndIsActive(any(), any(), any());
    }

    @Test
    void listProducts_byCategoryId_filtersCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, products.size());
        
        when(productRepository.findByCategoryId(testCategoryId, pageable)).thenReturn(productPage);

        // Act
        Page<Product> result = catalogService.listProducts(testCategoryId, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        
        verify(productRepository).findByCategoryId(testCategoryId, pageable);
        verify(productRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listProducts_byIsActive_filtersCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, products.size());
        
        when(productRepository.findByIsActive(true, pageable)).thenReturn(productPage);

        // Act
        Page<Product> result = catalogService.listProducts(null, true, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        
        verify(productRepository).findByIsActive(true, pageable);
        verify(productRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listProducts_byCategoryAndActive_filtersCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, products.size());
        
        when(productRepository.findByCategoryIdAndIsActive(testCategoryId, true, pageable))
                .thenReturn(productPage);

        // Act
        Page<Product> result = catalogService.listProducts(testCategoryId, true, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        
        verify(productRepository).findByCategoryIdAndIsActive(testCategoryId, true, pageable);
        verify(productRepository, never()).findAll(any(Pageable.class));
        verify(productRepository, never()).findByCategoryId(any(), any());
        verify(productRepository, never()).findByIsActive(any(), any());
    }

    @Test
    void listProducts_pagination_worksCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(1, 5); // Page 1, size 5
        List<Product> products = List.of(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 10); // Total 10 products
        
        when(productRepository.findAll(pageable)).thenReturn(productPage);

        // Act
        Page<Product> result = catalogService.listProducts(null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(10);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void listProducts_emptyResult_returnsEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        
        when(productRepository.findAll(pageable)).thenReturn(emptyPage);

        // Act
        Page<Product> result = catalogService.listProducts(null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }
}
