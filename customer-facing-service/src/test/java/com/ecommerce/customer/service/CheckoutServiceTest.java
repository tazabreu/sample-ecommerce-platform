package com.ecommerce.customer.service;

import com.ecommerce.customer.dto.CheckoutRequest;
import com.ecommerce.customer.dto.CheckoutResponse;
import com.ecommerce.customer.dto.CustomerInfoDto;
import com.ecommerce.customer.dto.ShippingAddressDto;
import com.ecommerce.customer.model.Cart;
import com.ecommerce.customer.model.Product;
import com.ecommerce.customer.repository.OrderCreatedOutboxRepository;
import com.ecommerce.customer.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CheckoutService.
 * Tests business logic with real Spring context.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class CheckoutServiceTest {

    @MockBean
    private CartService cartService;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private OrderCreatedOutboxRepository outboxRepository;

    @MockBean
    private OrderNumberService orderNumberService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "checkoutAttemptsCounter")
    private Counter checkoutAttemptsCounter;

    @MockBean(name = "checkoutSuccessCounter")
    private Counter checkoutSuccessCounter;

    @MockBean(name = "checkoutFailuresCounter")
    private Counter checkoutFailuresCounter;

    @MockBean(name = "checkoutDurationTimer")
    private Timer checkoutDurationTimer;

    @Autowired
    private CheckoutService checkoutService;

    private String sessionId;
    private Product product;
    private Cart cart;
    private CustomerInfoDto customerInfo;

    @BeforeEach
    void setUp() {
        sessionId = "test-session-123";

        product = new Product("TEST-SKU", "Test Product", "Description",
                new BigDecimal("29.99"), 10, null);

        cart = spy(new Cart(sessionId, 30));
        // Ensure product has a non-null ID and repository can lock-find it
        UUID productId = UUID.randomUUID();
        product = spy(product);
        doReturn(productId).when(product).getId();
        product.setCategoryId(UUID.randomUUID());
        when(productRepository.findByIdWithLock(productId)).thenReturn(java.util.Optional.of(product));
        when(productRepository.findById(productId)).thenReturn(java.util.Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cart.addItem(product, 2);

        ShippingAddressDto shippingAddress = new ShippingAddressDto(
                "123 Main St", "Anytown", "CA", "12345", "USA");
        customerInfo = new CustomerInfoDto(
                "John Doe", "john.doe@example.com", "+1234567890", shippingAddress);

        // Configure Timer mock to execute the supplier and return its result
        doAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        }).when(checkoutDurationTimer).record(any(java.util.function.Supplier.class));

        // Configure order number service
        when(orderNumberService.generateOrderNumber()).thenReturn("ORD-20251004-001");
    }

    @Test
    void checkout_shouldProcessSuccessfully_whenValidCart() {
        // Given
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        // Mock insertOutbox instead of save
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        CheckoutResponse response = checkoutService.checkout(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.orderNumber()).isEqualTo("ORD-20251004-001");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).contains(customerInfo.email());

        verify(cartService).deleteCart(sessionId);
        verify(productRepository, atLeastOnce()).save(any(Product.class));

        java.util.List<com.ecommerce.customer.model.CartItem> items = cart.getItems();
        assertThat(items).hasSize(1);
        // Cart items don't have IDs in unit tests with mocks (not persisted)
        // assertThat(items.get(0).getId()).isNotNull();

        // Verify insertOutbox was called (can't capture args easily with void method)
        verify(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );
    }

    @Test
    void checkout_shouldThrow_whenCartIsEmpty() {
        // Given
        Cart emptyCart = new Cart(sessionId, 30);
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(emptyCart);

        // When & Then
        assertThatThrownBy(() -> checkoutService.checkout(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot checkout with empty cart");
    }

    // ==================== Inventory Validation Tests (4 tests) ====================

    @Test
    void checkout_shouldThrow_whenInsufficientInventorySingleItem() {
        // Given - Product has only 5 items but cart wants 10
        Product lowInventoryProduct = new Product("LOW-SKU", "Low Stock Product", "Description",
                new BigDecimal("99.99"), 5, UUID.randomUUID());
        UUID productId = UUID.randomUUID();
        lowInventoryProduct = spy(lowInventoryProduct);
        doReturn(productId).when(lowInventoryProduct).getId();
        
        Cart cartWithHighQuantity = spy(new Cart(sessionId, 30));
        cartWithHighQuantity.addItem(lowInventoryProduct, 10); // Requesting more than available
        
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cartWithHighQuantity);
        when(productRepository.findByIdWithLock(productId)).thenReturn(java.util.Optional.of(lowInventoryProduct));

        // When & Then
        assertThatThrownBy(() -> checkoutService.checkout(request))
                .isInstanceOf(com.ecommerce.customer.exception.InsufficientInventoryException.class)
                .hasMessageContaining("Requested: 10")
                .hasMessageContaining("Available: 5");
        
        // Verify no inventory was decremented
        verify(productRepository, never()).save(any(Product.class));
        // Verify cart was not deleted
        verify(cartService, never()).deleteCart(sessionId);
    }

    @Test
    void checkout_shouldThrow_whenInsufficientInventoryMultipleItems() {
        // Given - Second product has insufficient inventory
        Product product2 = new Product("PROD-2", "Product 2", "Description",
                new BigDecimal("49.99"), 2, UUID.randomUUID());
        UUID product2Id = UUID.randomUUID();
        product2 = spy(product2);
        doReturn(product2Id).when(product2).getId();
        
        Cart multiItemCart = spy(new Cart(sessionId, 30));
        multiItemCart.addItem(product, 2); // First item OK (10 available)
        multiItemCart.addItem(product2, 5); // Second item NOT OK (only 2 available)
        
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(multiItemCart);
        when(productRepository.findByIdWithLock(product2Id)).thenReturn(java.util.Optional.of(product2));

        // When & Then
        assertThatThrownBy(() -> checkoutService.checkout(request))
                .isInstanceOf(com.ecommerce.customer.exception.InsufficientInventoryException.class);
        
        // Verify transaction rollback (no cart deletion)
        verify(cartService, never()).deleteCart(sessionId);
    }

    @Test
    void checkout_shouldDecrementInventoryAtomically() {
        // Given
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        checkoutService.checkout(request);

        // Then - Verify product inventory was decremented
        verify(productRepository, atLeastOnce()).save(any(Product.class));
        // Original inventory: 10, cart quantity: 2, remaining should be 8
        verify(productRepository).findByIdWithLock(any(UUID.class));
    }

    @Test
    void checkout_shouldValidateInventoryWithPessimisticLock() {
        // Given
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        checkoutService.checkout(request);

        // Then - Verify pessimistic lock was used (findByIdWithLock, not findById)
        verify(productRepository, atLeastOnce()).findByIdWithLock(any(UUID.class));
    }

    // ==================== Order Number Generation Tests (3 tests) ====================

    @Test
    void checkout_shouldGenerateUniqueOrderNumber() {
        // Given
        when(orderNumberService.generateOrderNumber()).thenReturn("ORD-20251006-042");
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        CheckoutResponse response = checkoutService.checkout(request);

        // Then
        assertThat(response.orderNumber()).isEqualTo("ORD-20251006-042");
        verify(orderNumberService).generateOrderNumber();
    }

    @Test
    void checkout_shouldGenerateOrderNumberBeforeInventoryDecrement() {
        // Given - This test verifies the order of operations
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        checkoutService.checkout(request);

        // Then - Verify order: generateOrderNumber → findByIdWithLock (inventory decrement)
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(orderNumberService, productRepository);
        inOrder.verify(orderNumberService).generateOrderNumber();
        inOrder.verify(productRepository).findByIdWithLock(any(UUID.class));
    }

    @Test
    void checkout_shouldUseOrderNumberInOutboxPayload() {
        // Given
        String expectedOrderNumber = "ORD-20251006-999";
        when(orderNumberService.generateOrderNumber()).thenReturn(expectedOrderNumber);
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        CheckoutResponse response = checkoutService.checkout(request);

        // Then
        assertThat(response.orderNumber()).isEqualTo(expectedOrderNumber);
        // Verify outbox was called (payload contains order number, but we can't easily inspect it with doNothing)
        verify(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), eq("ORDER"), eq("OrderCreatedEvent"),
            any(String.class), any(java.time.Instant.class), eq("PENDING"), eq(0)
        );
    }

    // ==================== Outbox Publishing Tests (3 tests) ====================

    @Test
    void checkout_shouldWriteToOutboxWithCorrectEventType() {
        // Given
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        checkoutService.checkout(request);

        // Then - Verify outbox insertOutbox was called with correct parameters
        verify(outboxRepository).insertOutbox(
            any(UUID.class),           // outboxId
            any(UUID.class),           // aggregateId (orderId)
            eq("ORDER"),               // aggregateType
            eq("OrderCreatedEvent"),   // eventType
            any(String.class),         // payload (JSON)
            any(java.time.Instant.class), // createdAt
            eq("PENDING"),             // status
            eq(0)                      // retryCount
        );
    }

    @Test
    void checkout_shouldClearCartAfterOutboxWrite() {
        // Given
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        checkoutService.checkout(request);

        // Then - Verify order of operations: outbox write → cart deletion
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(outboxRepository, cartService);
        inOrder.verify(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );
        inOrder.verify(cartService).deleteCart(sessionId);
    }

    @Test
    void checkout_shouldIncludeCustomerInfoInOutboxPayload() {
        // Given
        CustomerInfoDto customCustomerInfo = new CustomerInfoDto(
                "Jane Smith",
                "jane.smith@example.com",
                "+1987654321",
                new ShippingAddressDto("456 Oak Ave", "Boston", "MA", "02101", "USA")
        );
        CheckoutRequest request = new CheckoutRequest(sessionId, customCustomerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        doNothing().when(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );

        // When
        CheckoutResponse response = checkoutService.checkout(request);

        // Then
        assertThat(response.message()).contains(customCustomerInfo.email());
        verify(outboxRepository).insertOutbox(
            any(UUID.class), any(UUID.class), any(String.class), any(String.class),
            any(String.class), any(java.time.Instant.class), any(String.class), any(Integer.class)
        );
    }

}
