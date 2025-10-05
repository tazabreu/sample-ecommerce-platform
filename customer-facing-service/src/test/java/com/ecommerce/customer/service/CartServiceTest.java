package com.ecommerce.customer.service;

import com.ecommerce.customer.exception.InsufficientInventoryException;
import com.ecommerce.customer.exception.ResourceNotFoundException;
import com.ecommerce.customer.model.Cart;
import com.ecommerce.customer.model.CartItem;
import com.ecommerce.customer.model.Product;
import com.ecommerce.customer.repository.CartRepository;
import com.ecommerce.customer.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CartService.
 * Tests business logic with real Spring context.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext
class CartServiceTest {

    @MockBean
    private CartRepository cartRepository;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private RedisTemplate<String, Cart> cartRedisTemplate;

    @MockBean
    private ValueOperations<String, Cart> valueOperations;

    @MockBean(name = "cartItemsAddedCounter")
    private Counter cartItemsAddedCounter;

    @Autowired
    private CartService cartService;

    private UUID productId;
    private Product product;
    private String sessionId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        sessionId = "test-session-123";

        // Create product using constructor (we'll need to mock the category)
        product = new Product("TEST-SKU", "Test Product", "Description",
                new BigDecimal("29.99"), 100, null);
        // Simulate JPA setting the ID
        product = spy(product);
        doReturn(productId).when(product).getId();
        when(product.getIsActive()).thenReturn(true);

        // Setup Redis template mock
        when(cartRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getCart_shouldReturnFromRedis_whenExists() {
        // Given
        Cart cart = new Cart(sessionId, 30);
        when(valueOperations.get("cart:" + sessionId)).thenReturn(cart);

        // When
        Cart result = cartService.getCart(sessionId);

        // Then
        assertThat(result).isEqualTo(cart);
        verify(cartRepository, never()).findWithItemsBySessionId(any());
    }

    @Test
    void getCart_shouldReturnFromDatabase_whenNotInRedis() {
        // Given
        Cart cart = new Cart(sessionId, 30);
        cart = spy(cart);
        UUID cartId = UUID.randomUUID();
        doReturn(cartId).when(cart).getId();
        when(cart.isExpired()).thenReturn(false);

        when(valueOperations.get("cart:" + sessionId)).thenReturn(null);
        when(cartRepository.findWithItemsBySessionId(sessionId)).thenReturn(Optional.of(cart));

        // When
        Cart result = cartService.getCart(sessionId);

        // Then
        assertThat(result).isEqualTo(cart);
        verify(valueOperations).set(eq("cart:" + sessionId), eq(cart), any(Duration.class));
    }

    @Test
    void getCart_shouldCreateNewCart_whenNotFoundAnywhere() {
        // Given
        Cart savedCart = new Cart(sessionId, 30);
        savedCart = spy(savedCart);
        UUID cartId = UUID.randomUUID();
        doReturn(cartId).when(savedCart).getId();

        when(valueOperations.get("cart:" + sessionId)).thenReturn(null);
        when(cartRepository.findWithItemsBySessionId(sessionId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(savedCart);

        // When
        Cart result = cartService.getCart(sessionId);

        // Then
        assertThat(result).isEqualTo(savedCart);
        verify(cartRepository).save(any(Cart.class));
        verify(valueOperations).set(eq("cart:" + sessionId), eq(savedCart), any(Duration.class));
    }

    @Test
    void getCart_shouldCreateNewCart_whenExpiredInDatabase() {
        // Given
        Cart expiredCart = new Cart(sessionId, 30);
        expiredCart = spy(expiredCart);
        when(expiredCart.isExpired()).thenReturn(true);

        Cart savedCart = new Cart(sessionId, 30);
        savedCart = spy(savedCart);
        UUID cartId = UUID.randomUUID();
        doReturn(cartId).when(savedCart).getId();

        when(valueOperations.get("cart:" + sessionId)).thenReturn(null);
        when(cartRepository.findWithItemsBySessionId(sessionId)).thenReturn(Optional.of(expiredCart));
        when(cartRepository.save(any(Cart.class))).thenReturn(savedCart);

        // When
        Cart result = cartService.getCart(sessionId);

        // Then
        assertThat(result).isEqualTo(savedCart);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void addItemToCart_shouldAddItem_whenValidProductAndInventory() {
        // Given
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        Cart cart = new Cart(sessionId, 30);
        when(cartRepository.findWithItemsBySessionId(sessionId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        // When
        Cart result = cartService.addItemToCart(sessionId, productId, 2);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProduct()).isEqualTo(product);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        verify(cartRepository).save(cart);
    }

    @Test
    void addItemToCart_shouldThrow_whenProductNotFound() {
        // Given
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cartService.addItemToCart(sessionId, productId, 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }

    @Test
    void addItemToCart_shouldThrow_whenProductNotActive() {
        // Given
        when(product.getIsActive()).thenReturn(false);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When & Then
        assertThatThrownBy(() -> cartService.addItemToCart(sessionId, productId, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product is not active");
    }

    @Test
    void addItemToCart_shouldThrow_whenInsufficientInventory() {
        // Given
        when(product.getInventoryQuantity()).thenReturn(5);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When & Then
        assertThatThrownBy(() -> cartService.addItemToCart(sessionId, productId, 10))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("Insufficient inventory");
    }
}
