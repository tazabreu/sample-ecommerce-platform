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
        when(productRepository.findByIdWithLock(productId)).thenReturn(java.util.Optional.of(product));

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
        when(outboxRepository.save(any())).thenReturn(null);

        // When
        CheckoutResponse response = checkoutService.checkout(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.orderNumber()).isEqualTo("ORD-20251004-001");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).contains(customerInfo.email());

        verify(cartService).deleteCart(sessionId);
        verify(outboxRepository).save(any());
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

}
