package com.ecommerce.customer.service;

import com.ecommerce.customer.dto.CheckoutRequest;
import com.ecommerce.customer.dto.CheckoutResponse;
import com.ecommerce.customer.dto.CustomerInfoDto;
import com.ecommerce.customer.dto.ShippingAddressDto;
import com.ecommerce.customer.event.OrderCreatedEventPublisher;
import com.ecommerce.customer.model.Cart;
import com.ecommerce.customer.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CheckoutService.
 * Tests business logic in isolation using mocks for dependencies.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private CartService cartService;

    @Mock
    private OrderCreatedEventPublisher eventPublisher;

    @InjectMocks
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
        product = spy(product);

        cart = new Cart(sessionId, 30);
        cart.addItem(product, 2);

        ShippingAddressDto shippingAddress = new ShippingAddressDto(
                "123 Main St", "Anytown", "CA", "12345", "USA");
        customerInfo = new CustomerInfoDto(
                "John Doe", "john.doe@example.com", "+1234567890", shippingAddress);
    }

    @Test
    void checkout_shouldProcessSuccessfully_whenValidCart() {
        // Given
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        when(eventPublisher.publishOrderCreated(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        CheckoutResponse response = checkoutService.checkout(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.orderNumber()).matches("^ORD-\\d{8}-\\d{3}$");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).contains(customerInfo.email());

        verify(cartService).deleteCart(sessionId);
        verify(eventPublisher).publishOrderCreated(any());
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

    @Test
    void checkout_shouldThrow_whenEventPublishingFails() {
        // Given
        CheckoutRequest request = new CheckoutRequest(sessionId, customerInfo);
        when(cartService.getCart(sessionId)).thenReturn(cart);
        RuntimeException publishException = new RuntimeException("Kafka unavailable");
        when(eventPublisher.publishOrderCreated(any()))
                .thenReturn(CompletableFuture.failedFuture(publishException));

        // When & Then
        assertThatThrownBy(() -> checkoutService.checkout(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to publish order event");
    }
}
