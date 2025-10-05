package com.ecommerce.customer.service;

import com.ecommerce.customer.dto.CheckoutRequest;
import com.ecommerce.customer.dto.CheckoutResponse;
import com.ecommerce.customer.dto.CustomerInfoDto;
import com.ecommerce.customer.exception.InsufficientInventoryException;
import com.ecommerce.customer.exception.ResourceNotFoundException;
import com.ecommerce.customer.model.Cart;
import com.ecommerce.customer.model.CartItem;
import com.ecommerce.customer.model.OrderCreatedOutbox;
import com.ecommerce.customer.model.Product;
import com.ecommerce.customer.repository.OrderCreatedOutboxRepository;
import com.ecommerce.customer.repository.ProductRepository;
import com.ecommerce.shared.event.CustomerEvent;
import com.ecommerce.shared.event.OrderCreatedEvent;
import com.ecommerce.shared.event.OrderItemEvent;
import com.ecommerce.shared.event.ShippingAddressEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for handling checkout operations.
 *
 * <p>Checkout Flow:</p>
 * <ol>
 *   <li>Validate cart exists and is not empty</li>
 *   <li>Generate unique order number via DB-backed sequence (ORD-YYYYMMDD-###)</li>
 *   <li>Decrement product inventory with pessimistic locking</li>
 *   <li>Write OrderCreatedEvent to transactional outbox</li>
 *   <li>Clear the cart</li>
 *   <li>Return CheckoutResponse</li>
 * </ol>
 *
 * <p>Transactional Guarantees:</p>
 * <ul>
 *   <li>SERIALIZABLE isolation for inventory updates</li>
 *   <li>Pessimistic write locks on products</li>
 *   <li>All-or-nothing inventory decrement</li>
 *   <li>DB-backed order number generation prevents duplicates across instances</li>
 *   <li>Transactional outbox ensures event is persisted in same transaction</li>
 *   <li>Background publisher handles Kafka delivery asynchronously</li>
 * </ul>
 */
@Service
public class CheckoutService {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutService.class);

    private final CartService cartService;
    private final ProductRepository productRepository;
    private final OrderCreatedOutboxRepository outboxRepository;
    private final OrderNumberService orderNumberService;
    private final ObjectMapper objectMapper;
    private final Counter checkoutAttemptsCounter;
    private final Counter checkoutSuccessCounter;
    private final Counter checkoutFailuresCounter;
    private final Timer checkoutDurationTimer;

    public CheckoutService(
            CartService cartService,
            ProductRepository productRepository,
            OrderCreatedOutboxRepository outboxRepository,
            OrderNumberService orderNumberService,
            ObjectMapper objectMapper,
            @Qualifier("checkoutAttemptsCounter") Counter checkoutAttemptsCounter,
            @Qualifier("checkoutSuccessCounter") Counter checkoutSuccessCounter,
            @Qualifier("checkoutFailuresCounter") Counter checkoutFailuresCounter,
            @Qualifier("checkoutDurationTimer") Timer checkoutDurationTimer
    ) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.outboxRepository = outboxRepository;
        this.orderNumberService = orderNumberService;
        this.objectMapper = objectMapper;
        this.checkoutAttemptsCounter = checkoutAttemptsCounter;
        this.checkoutSuccessCounter = checkoutSuccessCounter;
        this.checkoutFailuresCounter = checkoutFailuresCounter;
        this.checkoutDurationTimer = checkoutDurationTimer;
    }

    /**
     * Process checkout for a cart.
     * 
     * <p>This method is transactional with SERIALIZABLE isolation to prevent
     * race conditions on inventory updates.</p>
     *
     * @param request the checkout request containing session ID and customer info
     * @return checkout response with order number and status
     * @throws ResourceNotFoundException if cart not found
     * @throws IllegalStateException if cart is empty
     * @throws InsufficientInventoryException if insufficient inventory for any product
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CheckoutResponse checkout(CheckoutRequest request) {
        // Increment checkout attempts counter
        checkoutAttemptsCounter.increment();

        // Time the checkout process
        return checkoutDurationTimer.record(() -> {
            try {
                String sessionId = request.sessionId();
                CustomerInfoDto customerInfo = request.customerInfo();

                logger.info("Starting checkout - sessionId: {}, customer: {}",
                        sessionId, customerInfo.email());

                // 1. Get cart and validate it's not empty
                Cart cart = cartService.getCart(sessionId);
                if (cart.isEmpty()) {
                    throw new IllegalStateException("Cannot checkout with empty cart");
                }

                logger.info("Checkout cart validation passed - cartId: {}, items: {}, subtotal: {}",
                        cart.getId(), cart.getTotalItemCount(), cart.getSubtotal());

                // 2. Generate order number via DB-backed sequence
                String orderNumber = orderNumberService.generateOrderNumber();
                UUID orderId = UUID.randomUUID();

                logger.info("Generated order - orderNumber: {}, orderId: {}", orderNumber, orderId);

                // 3. Decrement inventory with pessimistic locking
                decrementInventory(cart);

                // 4. Create OrderCreatedEvent and write to outbox
                OrderCreatedEvent event = createOrderCreatedEvent(
                        orderId,
                        orderNumber,
                        cart,
                        customerInfo
                );

                writeToOutbox(orderId, event);

                // 5. Clear the cart
                cartService.deleteCart(sessionId);

                logger.info("Checkout completed successfully - orderNumber: {}, orderTotal: {}",
                        orderNumber, cart.getSubtotal());

                // Increment checkout success counter
                checkoutSuccessCounter.increment();

                // 6. Return response
                return new CheckoutResponse(
                        orderNumber,
                        "PENDING",
                        "Order submitted successfully. You will receive a confirmation email at " +
                                customerInfo.email()
                );
            } catch (Exception e) {
                // Increment checkout failures counter
                checkoutFailuresCounter.increment();
                throw e;
            }
        });
    }

    /**
     * Decrement inventory for all cart items with pessimistic locking.
     * 
     * <p>Uses pessimistic write locks to prevent concurrent updates to inventory.</p>
     *
     * @param cart the cart with items to process
     * @throws InsufficientInventoryException if any product has insufficient inventory
     */
    private void decrementInventory(Cart cart) {
        logger.debug("Decrementing inventory for {} items", cart.getItems().size());

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            int requestedQuantity = cartItem.getQuantity();

            // Reload product with pessimistic write lock
            Product lockedProduct = productRepository.findByIdWithLock(product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", product.getId()));

            // Validate inventory availability
            if (lockedProduct.getInventoryQuantity() < requestedQuantity) {
                logger.error("Insufficient inventory - productId: {}, requested: {}, available: {}", 
                        lockedProduct.getId(), requestedQuantity, lockedProduct.getInventoryQuantity());
                throw new InsufficientInventoryException(
                        lockedProduct.getId(),
                        requestedQuantity,
                        lockedProduct.getInventoryQuantity()
                );
            }

            // Decrement inventory
            int newQuantity = lockedProduct.getInventoryQuantity() - requestedQuantity;
            lockedProduct.setInventoryQuantity(newQuantity);
            productRepository.save(lockedProduct);

            logger.debug("Decremented inventory - productId: {}, sku: {}, old: {}, new: {}", 
                    lockedProduct.getId(), 
                    lockedProduct.getSku(), 
                    lockedProduct.getInventoryQuantity() + requestedQuantity, 
                    newQuantity);
        }

        logger.info("Successfully decremented inventory for all cart items");
    }

    /**
     * Create OrderCreatedEvent from cart and customer info.
     *
     * @param orderId the generated order ID
     * @param orderNumber the generated order number
     * @param cart the cart being checked out
     * @param customerInfo customer information
     * @return constructed OrderCreatedEvent
     */
    private OrderCreatedEvent createOrderCreatedEvent(
            UUID orderId,
            String orderNumber,
            Cart cart,
            CustomerInfoDto customerInfo
    ) {
        // Get correlation ID from MDC or generate new one
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = "checkout-" + UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }

        // Map cart items to order item events
        List<OrderItemEvent> orderItems = cart.getItems().stream()
                .map(cartItem -> new OrderItemEvent(
                        cartItem.getProduct().getId(),
                        cartItem.getProduct().getSku(),
                        cartItem.getProduct().getName(),
                        cartItem.getQuantity(),
                        cartItem.getPriceSnapshot(),
                        cartItem.getSubtotal()
                ))
                .collect(Collectors.toList());

        // Map customer info to customer event
        CustomerEvent customer = new CustomerEvent(
                customerInfo.name(),
                customerInfo.email(),
                customerInfo.phone(),
                new ShippingAddressEvent(
                        customerInfo.shippingAddress().street(),
                        customerInfo.shippingAddress().city(),
                        customerInfo.shippingAddress().state(),
                        customerInfo.shippingAddress().postalCode(),
                        customerInfo.shippingAddress().country()
                )
        );

        return new OrderCreatedEvent(
                correlationId,
                orderId,
                orderNumber,
                customer,
                orderItems,
                cart.getSubtotal(),
                cart.getId()
        );
    }

    /**
     * Write OrderCreatedEvent to transactional outbox.
     * Event will be published asynchronously by background publisher.
     *
     * @param orderId the order ID
     * @param event the order created event
     */
    private void writeToOutbox(UUID orderId, OrderCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OrderCreatedOutbox outbox = new OrderCreatedOutbox(orderId, payload);
            outboxRepository.save(outbox);

            logger.info("Wrote OrderCreatedEvent to outbox - orderId: {}, outboxId: {}",
                    orderId, outbox.getId());

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize OrderCreatedEvent to JSON - orderId: {}", orderId, e);
            throw new RuntimeException("Failed to write event to outbox", e);
        }
    }
}


