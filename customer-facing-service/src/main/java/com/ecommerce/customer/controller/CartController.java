package com.ecommerce.customer.controller;

import com.ecommerce.customer.dto.AddCartItemRequest;
import com.ecommerce.customer.dto.CartDto;
import com.ecommerce.customer.dto.UpdateCartItemRequest;
import com.ecommerce.customer.mapper.CartMapper;
import com.ecommerce.customer.service.CartService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing shopping carts.
 * 
 * <p>Cart operations are session-based (guest checkout model).
 * No authentication required - cart identified by sessionId.</p>
 * 
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/v1/carts/{sessionId} - Get cart by session ID</li>
 *   <li>DELETE /api/v1/carts/{sessionId} - Clear cart</li>
 *   <li>POST /api/v1/carts/{sessionId}/items - Add item to cart</li>
 *   <li>PUT /api/v1/carts/{sessionId}/items/{cartItemId} - Update item quantity</li>
 *   <li>DELETE /api/v1/carts/{sessionId}/items/{cartItemId} - Remove item from cart</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    private final CartService cartService;
    private final CartMapper cartMapper;

    public CartController(CartService cartService, CartMapper cartMapper) {
        this.cartService = cartService;
        this.cartMapper = cartMapper;
    }

    /**
     * Get cart by session ID.
     * Creates a new empty cart if one doesn't exist for the session.
     *
     * @param sessionId the session identifier
     * @return the cart
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<CartDto> getCart(@PathVariable String sessionId) {
        logger.info("Getting cart for session: {}", sessionId);
        CartDto cart = cartMapper.toDto(cartService.getCart(sessionId));
        logger.info("Cart retrieved - sessionId: {}, items: {}, subtotal: {}", 
                sessionId, cart.items().size(), cart.subtotal());
        return ResponseEntity.ok(cart);
    }

    /**
     * Clear cart (remove all items).
     *
     * @param sessionId the session identifier
     * @return 204 No Content
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> clearCart(@PathVariable String sessionId) {
        logger.info("Clearing cart for session: {}", sessionId);
        cartService.clearCart(sessionId);
        logger.info("Cart cleared - sessionId: {}", sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Add item to cart.
     * If item already exists, quantity is added to existing quantity.
     *
     * @param sessionId the session identifier
     * @param request the add item request
     * @return the updated cart
     */
    @PostMapping("/{sessionId}/items")
    public ResponseEntity<CartDto> addItemToCart(
            @PathVariable String sessionId,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        logger.info("Adding item to cart - sessionId: {}, productId: {}, quantity: {}", 
                sessionId, request.productId(), request.quantity());
        
        CartDto cart = cartMapper.toDto(cartService.addItemToCart(sessionId, request.productId(), request.quantity()));
        
        logger.info("Item added to cart - sessionId: {}, items: {}, subtotal: {}", 
                sessionId, cart.items().size(), cart.subtotal());
        
        return ResponseEntity.ok(cart);
    }

    /**
     * Update cart item quantity.
     *
     * @param sessionId the session identifier
     * @param cartItemId the cart item ID
     * @param request the update request
     * @return the updated cart
     */
    @PutMapping("/{sessionId}/items/{cartItemId}")
    public ResponseEntity<CartDto> updateCartItem(
            @PathVariable String sessionId,
            @PathVariable UUID cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        logger.info("Updating cart item - sessionId: {}, cartItemId: {}, quantity: {}", 
                sessionId, cartItemId, request.quantity());
        
        CartDto cart = cartMapper.toDto(cartService.updateCartItemQuantity(sessionId, cartItemId, request.quantity()));
        
        logger.info("Cart item updated - sessionId: {}, items: {}, subtotal: {}", 
                sessionId, cart.items().size(), cart.subtotal());
        
        return ResponseEntity.ok(cart);
    }

    /**
     * Remove item from cart.
     *
     * @param sessionId the session identifier
     * @param cartItemId the cart item ID
     * @return the updated cart
     */
    @DeleteMapping("/{sessionId}/items/{cartItemId}")
    public ResponseEntity<CartDto> removeCartItem(
            @PathVariable String sessionId,
            @PathVariable UUID cartItemId
    ) {
        logger.info("Removing cart item - sessionId: {}, cartItemId: {}", sessionId, cartItemId);
        
        CartDto cart = cartMapper.toDto(cartService.removeCartItem(sessionId, cartItemId));
        
        logger.info("Cart item removed - sessionId: {}, items: {}, subtotal: {}", 
                sessionId, cart.items().size(), cart.subtotal());
        
        return ResponseEntity.ok(cart);
    }
}


