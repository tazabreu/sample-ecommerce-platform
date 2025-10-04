package com.ecommerce.customer.controller;

import com.ecommerce.customer.dto.CheckoutRequest;
import com.ecommerce.customer.dto.CheckoutResponse;
import com.ecommerce.customer.exception.InsufficientInventoryException;
import com.ecommerce.customer.service.CheckoutService;
import com.ecommerce.customer.service.IdempotencyService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST controller for checkout operations with idempotency support.
 *
 * <p>Checkout Flow:</p>
 * <ol>
 *   <li>Check Idempotency-Key header (required)</li>
 *   <li>Check for cached response (idempotent replay)</li>
 *   <li>Validate cart is not empty</li>
 *   <li>Validate customer information</li>
 *   <li>Generate order number via DB-backed sequence</li>
 *   <li>Decrement inventory (with pessimistic locking)</li>
 *   <li>Publish OrderCreatedEvent to Kafka</li>
 *   <li>Clear cart</li>
 *   <li>Store idempotency record</li>
 *   <li>Return order confirmation</li>
 * </ol>
 *
 * <p>Idempotency Guarantees:</p>
 * <ul>
 *   <li>Idempotency-Key header is required</li>
 *   <li>Same key + same request → cached response (200 OK)</li>
 *   <li>Same key + different request → 422 Unprocessable Entity</li>
 *   <li>Records expire after 24 hours</li>
 * </ul>
 *
 * <p>Error Handling:</p>
 * <ul>
 *   <li>Missing Idempotency-Key → 400 Bad Request</li>
 *   <li>Idempotency key conflict → 422 Unprocessable Entity</li>
 *   <li>Insufficient inventory → 409 Conflict</li>
 *   <li>Cart not found or empty → 400 Bad Request</li>
 *   <li>Invalid customer info → 400 Bad Request (validation)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final CheckoutService checkoutService;
    private final IdempotencyService idempotencyService;

    public CheckoutController(CheckoutService checkoutService, IdempotencyService idempotencyService) {
        this.checkoutService = checkoutService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Process checkout for a session's cart with idempotency support.
     * Public endpoint - no authentication required (guest checkout).
     *
     * @param idempotencyKey the idempotency key from header (required)
     * @param request the checkout request with session ID and customer info
     * @return checkout response with order number and status
     */
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = true) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request
    ) {
        logger.info("Processing checkout - sessionId: {}, customerEmail: {}, idempotencyKey: {}",
                request.sessionId(), request.customerInfo().email(), idempotencyKey);

        // Check for idempotent replay
        Optional<ResponseEntity<Object>> cachedResponse = idempotencyService.checkIdempotency(idempotencyKey, request);
        if (cachedResponse.isPresent()) {
            logger.info("Returning cached response for idempotency key: {}", idempotencyKey);
            @SuppressWarnings("unchecked")
            ResponseEntity<CheckoutResponse> typedResponse = (ResponseEntity<CheckoutResponse>) (ResponseEntity<?>) cachedResponse.get();
            return typedResponse;
        }

        try {
            CheckoutResponse response = checkoutService.checkout(request);

            logger.info("Checkout successful - orderNumber: {}, sessionId: {}",
                    response.orderNumber(), request.sessionId());

            // Store idempotency record
            idempotencyService.storeIdempotency(idempotencyKey, request, HttpStatus.CREATED.value(), response);

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


