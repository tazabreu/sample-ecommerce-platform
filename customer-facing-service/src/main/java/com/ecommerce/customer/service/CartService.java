package com.ecommerce.customer.service;

import com.ecommerce.customer.exception.InsufficientInventoryException;
import com.ecommerce.customer.exception.ResourceNotFoundException;
import com.ecommerce.customer.model.Cart;
import com.ecommerce.customer.model.CartItem;
import com.ecommerce.customer.model.Product;
import com.ecommerce.customer.repository.CartRepository;
import com.ecommerce.customer.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for managing shopping carts.
 * 
 * <p>Storage Strategy:</p>
 * <ul>
 *   <li>Redis: Primary storage for active carts (fast, TTL-based expiration)</li>
 *   <li>PostgreSQL: Secondary storage for analytics and abandoned cart recovery</li>
 * </ul>
 * 
 * <p>Business Logic:</p>
 * <ul>
 *   <li>Cart operations validate product existence and inventory availability</li>
 *   <li>Price snapshots are captured when items are added</li>
 *   <li>Cart expiration is managed via Redis TTL (30 minutes default)</li>
 * </ul>
 */
@Service
@Transactional
public class CartService {

    private static final Logger logger = LoggerFactory.getLogger(CartService.class);
    private static final String CART_REDIS_KEY_PREFIX = "cart:";
    private static final int CART_TTL_MINUTES = 30;

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final RedisTemplate<String, Cart> cartRedisTemplate;
    private final Counter cartItemsAddedCounter;

    public CartService(
            CartRepository cartRepository,
            ProductRepository productRepository,
            RedisTemplate<String, Cart> cartRedisTemplate,
            Counter cartItemsAddedCounter
    ) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.cartRedisTemplate = cartRedisTemplate;
        this.cartItemsAddedCounter = cartItemsAddedCounter;
    }

    /**
     * Get cart by session ID.
     * Checks Redis first, falls back to PostgreSQL if not found.
     * Creates a new cart if not found in either storage.
     *
     * @param sessionId the session identifier
     * @return the cart
     */
    @Transactional(readOnly = true)
    public Cart getCart(String sessionId) {
        logger.debug("Getting cart for session: {}", sessionId);

        // Try Redis first
        String redisKey = getRedisKey(sessionId);
        Cart cart = cartRedisTemplate.opsForValue().get(redisKey);

        if (cart != null) {
            logger.debug("Cart found in Redis for session: {}", sessionId);
            return cart;
        }

        // Fall back to PostgreSQL
        cart = cartRepository.findBySessionId(sessionId).orElse(null);

        if (cart != null && !cart.isExpired()) {
            logger.debug("Cart found in PostgreSQL for session: {}", sessionId);
            // Restore to Redis
            saveToRedis(cart);
            return cart;
        }

        // Create new cart if not found or expired
        logger.info("Creating new cart for session: {}", sessionId);
        cart = new Cart(sessionId, CART_TTL_MINUTES);
        cart = cartRepository.save(cart);
        saveToRedis(cart);

        return cart;
    }

    /**
     * Add an item to the cart.
     * Validates product existence, inventory availability, and captures price snapshot.
     *
     * @param sessionId the session identifier
     * @param productId the product ID
     * @param quantity the quantity to add
     * @return the updated cart
     * @throws ResourceNotFoundException if product not found
     * @throws InsufficientInventoryException if insufficient inventory
     */
    public Cart addItemToCart(String sessionId, UUID productId, int quantity) {
        logger.info("Adding item to cart - session: {}, product: {}, quantity: {}", 
                sessionId, productId, quantity);

        // Validate product exists and is active
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (!product.getIsActive()) {
            throw new IllegalArgumentException("Product is not active: " + productId);
        }

        // Validate inventory availability
        if (product.getInventoryQuantity() < quantity) {
            throw new InsufficientInventoryException(
                    productId, 
                    quantity, 
                    product.getInventoryQuantity()
            );
        }

        // Get or create cart
        Cart cart = getCart(sessionId);

        // Check if adding this quantity would exceed available inventory
        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElse(null);

        int totalQuantity = quantity;
        if (existingItem != null) {
            totalQuantity += existingItem.getQuantity();
        }

        if (product.getInventoryQuantity() < totalQuantity) {
            throw new InsufficientInventoryException(
                    productId, 
                    totalQuantity, 
                    product.getInventoryQuantity()
            );
        }

        // Add item to cart
        cart.addItem(product, quantity);

        // Save to both storages
        cart = cartRepository.save(cart);
        saveToRedis(cart);

        // Increment cart items added metric
        cartItemsAddedCounter.increment();

        logger.info("Added item to cart - cart id: {}, total items: {}",
                cart.getId(), cart.getTotalItemCount());

        return cart;
    }

    /**
     * Update cart item quantity.
     *
     * @param sessionId the session identifier
     * @param cartItemId the cart item ID
     * @param quantity the new quantity
     * @return the updated cart
     * @throws ResourceNotFoundException if cart or cart item not found
     * @throws InsufficientInventoryException if insufficient inventory
     */
    public Cart updateCartItemQuantity(String sessionId, UUID cartItemId, int quantity) {
        logger.info("Updating cart item - session: {}, cartItemId: {}, quantity: {}", 
                sessionId, cartItemId, quantity);

        Cart cart = getCart(sessionId);

        // Find the cart item
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", cartItemId));

        // Validate inventory availability
        Product product = cartItem.getProduct();
        if (product.getInventoryQuantity() < quantity) {
            throw new InsufficientInventoryException(
                    product.getId(), 
                    quantity, 
                    product.getInventoryQuantity()
            );
        }

        // Update quantity
        cart.updateItemQuantity(cartItemId, quantity);

        // Save to both storages
        cart = cartRepository.save(cart);
        saveToRedis(cart);

        logger.info("Updated cart item - cart id: {}, new subtotal: {}", 
                cart.getId(), cart.getSubtotal());

        return cart;
    }

    /**
     * Remove an item from the cart.
     *
     * @param sessionId the session identifier
     * @param cartItemId the cart item ID
     * @return the updated cart
     * @throws ResourceNotFoundException if cart or cart item not found
     */
    public Cart removeCartItem(String sessionId, UUID cartItemId) {
        logger.info("Removing cart item - session: {}, cartItemId: {}", sessionId, cartItemId);

        Cart cart = getCart(sessionId);

        // Remove item
        boolean removed = cart.removeItem(cartItemId);
        if (!removed) {
            throw new ResourceNotFoundException("CartItem", cartItemId);
        }

        // Save to both storages
        cart = cartRepository.save(cart);
        saveToRedis(cart);

        logger.info("Removed cart item - cart id: {}, remaining items: {}", 
                cart.getId(), cart.getTotalItemCount());

        return cart;
    }

    /**
     * Clear all items from the cart.
     *
     * @param sessionId the session identifier
     */
    public void clearCart(String sessionId) {
        logger.info("Clearing cart for session: {}", sessionId);

        Cart cart = getCart(sessionId);
        cart.clear();

        // Save to both storages
        cartRepository.save(cart);
        saveToRedis(cart);

        logger.info("Cleared cart - cart id: {}", cart.getId());
    }

    /**
     * Delete cart completely (used after checkout).
     *
     * @param sessionId the session identifier
     */
    public void deleteCart(String sessionId) {
        logger.info("Deleting cart for session: {}", sessionId);

        // Delete from Redis
        String redisKey = getRedisKey(sessionId);
        cartRedisTemplate.delete(redisKey);

        // Delete from PostgreSQL
        cartRepository.findBySessionId(sessionId)
                .ifPresent(cartRepository::delete);

        logger.info("Deleted cart for session: {}", sessionId);
    }

    /**
     * Save cart to Redis with TTL.
     *
     * @param cart the cart to save
     */
    private void saveToRedis(Cart cart) {
        String redisKey = getRedisKey(cart.getSessionId());
        cartRedisTemplate.opsForValue().set(
                redisKey, 
                cart, 
                Duration.ofMinutes(CART_TTL_MINUTES)
        );
        logger.debug("Saved cart to Redis: {}", redisKey);
    }

    /**
     * Generate Redis key for a session ID.
     *
     * @param sessionId the session ID
     * @return the Redis key
     */
    private String getRedisKey(String sessionId) {
        return CART_REDIS_KEY_PREFIX + sessionId;
    }
}


