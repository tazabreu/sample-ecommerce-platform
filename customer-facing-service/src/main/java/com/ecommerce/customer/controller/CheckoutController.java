package com.ecommerce.customer.controller;

import com.ecommerce.customer.dto.CheckoutRequest;
import com.ecommerce.customer.dto.CheckoutResponse;
import com.ecommerce.customer.exception.InsufficientInventoryException;
import com.ecommerce.customer.service.CheckoutService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for checkout operations.
 * 
 * <p>Checkout Flow:</p>
 * <ol>
 *   <li>Validate cart is not empty</li>
 *   <li>Validate customer information</li>
 *   <li>Generate order number</li>
 *   <li>Decrement inventory (with pessimistic locking)</li>
 *   <li>Publish OrderCreatedEvent to Kafka</li>
 *   <li>Clear cart</li>
 *   <li>Return order confirmation</li>
 * </ol>
 * 
 * <p>Error Handling:</p>
 * <ul>
 *   <li>Insufficient inventory → 409 Conflict</li>
 *   <li>Cart not found or empty → 400 Bad Request</li>
 *   <li>Invalid customer info → 400 Bad Request (validation)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /**
     * Process checkout for a session's cart.
     * Public endpoint - no authentication required (guest checkout).
     *
     * @param request the checkout request with session ID and customer info
     * @return checkout response with order number and status
     */
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        logger.info("Processing checkout - sessionId: {}, customerEmail: {}", 
                request.sessionId(), request.customerInfo().email());

        try {
            CheckoutResponse response = checkoutService.checkout(request);

            logger.info("Checkout successful - orderNumber: {}, sessionId: {}", 
                    response.orderNumber(), request.sessionId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (InsufficientInventoryException ex) {
            logger.warn("Checkout failed due to insufficient inventory - sessionId: {}, error: {}", 
                    request.sessionId(), ex.getMessage());
            
            // Re-throw to be handled by GlobalExceptionHandler
            throw ex;
        }
    }

    /**
     * Exception handler for insufficient inventory.
     * Returns 409 Conflict with error details.
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<CheckoutResponse> handleInsufficientInventory(InsufficientInventoryException ex) {
        CheckoutResponse response = new CheckoutResponse(
                null,
                "FAILED",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}


