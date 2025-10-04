package com.ecommerce.order.service;

import com.ecommerce.shared.event.OrderCreatedEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.PaymentTransaction;
import com.ecommerce.order.model.ProcessedEvent;
import com.ecommerce.order.payment.PaymentException;
import com.ecommerce.order.payment.PaymentRequest;
import com.ecommerce.order.payment.PaymentResult;
import com.ecommerce.order.payment.PaymentService;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for processing OrderCreatedEvent from Kafka.
 * 
 * <p>Event Processing Flow:</p>
 * <ol>
 *   <li>Check idempotency (eventId in processed_events table)</li>
 *   <li>Create Order entity with status=PENDING</li>
 *   <li>Create OrderItem entities (bulk insert)</li>
 *   <li>Create PaymentTransaction with status=PENDING</li>
 *   <li>Record eventId in processed_events table</li>
 *   <li>Trigger payment processing asynchronously</li>
 *   <li>Acknowledge Kafka message</li>
 * </ol>
 * 
 * <p>Idempotency:</p>
 * <ul>
 *   <li>Duplicate events are safely ignored based on eventId</li>
 *   <li>All-or-nothing transaction ensures consistency</li>
 * </ul>
 */
@Service
public class OrderProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingService.class);

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentService paymentService;
    private final PaymentCompletedService paymentCompletedService;

    public OrderProcessingService(
            OrderRepository orderRepository,
            ProcessedEventRepository processedEventRepository,
            PaymentService paymentService,
            PaymentCompletedService paymentCompletedService
    ) {
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
        this.paymentService = paymentService;
        this.paymentCompletedService = paymentCompletedService;
    }

    /**
     * Kafka listener for OrderCreatedEvent.
     * Processes events from the orders.created topic.
     *
     * @param event the order created event
     * @param partition the Kafka partition
     * @param offset the Kafka offset
     * @param ack the acknowledgment handle for manual commit
     */
    @KafkaListener(
            topics = "${kafka.topics.orders-created:orders.created}",
            groupId = "${spring.kafka.consumer.group-id:order-service-group}",
            containerFactory = "orderCreatedEventListenerFactory"
    )
    public void onOrderCreatedEvent(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        // Set correlation ID in MDC for tracing
        MDC.put("correlationId", event.correlationId());
        MDC.put("orderNumber", event.orderNumber());

        logger.info("Received OrderCreatedEvent - orderNumber: {}, partition: {}, offset: {}", 
                event.orderNumber(), partition, offset);

        try {
            processOrderCreatedEvent(event);
            
            // Acknowledge message after successful processing
            ack.acknowledge();
            
            logger.info("Successfully processed OrderCreatedEvent - orderNumber: {}", 
                    event.orderNumber());
        } catch (Exception ex) {
            logger.error("Failed to process OrderCreatedEvent - orderNumber: {}, error: {}", 
                    event.orderNumber(), ex.getMessage(), ex);
            
            // Don't acknowledge - message will be redelivered
            // In production, consider sending to DLQ after max retries
            throw new RuntimeException("Order processing failed", ex);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Process OrderCreatedEvent and create order with payment transaction.
     * Handles idempotency and triggers asynchronous payment processing.
     *
     * @param event the order created event
     */
    @Transactional
    public void processOrderCreatedEvent(OrderCreatedEvent event) {
        // 1. Check idempotency
        if (processedEventRepository.existsByEventId(event.eventId())) {
            logger.warn("Duplicate OrderCreatedEvent detected - eventId: {}, orderNumber: {}. Skipping.", 
                    event.eventId(), event.orderNumber());
            return;
        }

        logger.info("Processing new OrderCreatedEvent - orderNumber: {}, eventId: {}", 
                event.orderNumber(), event.eventId());

        // 2. Create Order entity
        Order order = createOrderFromEvent(event);
        order = orderRepository.save(order);

        logger.info("Created order - orderId: {}, orderNumber: {}, status: {}", 
                order.getId(), order.getOrderNumber(), order.getStatus());

        // 3. Create OrderItem entities
        List<OrderItem> orderItems = createOrderItemsFromEvent(event, order);
        orderItems.forEach(order::addItem);

        // 4. Create PaymentTransaction
        PaymentTransaction paymentTransaction = createPaymentTransaction(order);
        order.setPaymentTransaction(paymentTransaction);

        orderRepository.save(order);

        logger.info("Created order items and payment transaction - orderNumber: {}, items: {}, subtotal: {}", 
                order.getOrderNumber(), orderItems.size(), order.getSubtotal());

        // 5. Record event as processed
        ProcessedEvent processedEvent = new ProcessedEvent(event.eventId(), event.eventType());
        processedEventRepository.save(processedEvent);

        logger.info("Recorded processed event - eventId: {}, orderNumber: {}", 
                event.eventId(), event.orderNumber());

        // 6. Trigger payment processing asynchronously
        processPaymentAsync(order);
    }

    /**
     * Create Order entity from OrderCreatedEvent.
     *
     * @param event the event
     * @return the order entity
     */
    private Order createOrderFromEvent(OrderCreatedEvent event) {
        return new Order(
                event.orderNumber(),
                event.customer().name(),
                event.customer().email(),
                event.customer().phone(),
                Map.of(
                        "street", event.customer().shippingAddress().street(),
                        "city", event.customer().shippingAddress().city(),
                        "state", event.customer().shippingAddress().state(),
                        "postalCode", event.customer().shippingAddress().postalCode(),
                        "country", event.customer().shippingAddress().country()
                ),
                event.subtotal()
        );
    }

    /**
     * Create OrderItem entities from OrderCreatedEvent.
     *
     * @param event the event
     * @param order the order entity
     * @return list of order items
     */
    private List<OrderItem> createOrderItemsFromEvent(OrderCreatedEvent event, Order order) {
        return event.items().stream()
                .map(itemEvent -> new OrderItem(
                        order,
                        itemEvent.productId(),
                        itemEvent.productSku(),
                        itemEvent.productName(),
                        itemEvent.quantity(),
                        itemEvent.priceSnapshot()
                ))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Create PaymentTransaction for the order.
     *
     * @param order the order
     * @return payment transaction
     */
    private PaymentTransaction createPaymentTransaction(Order order) {
        return new PaymentTransaction(order, order.getSubtotal(), "MOCK");
    }

    /**
     * Process payment asynchronously to avoid blocking event processing.
     *
     * @param order the order to process payment for
     */
    @Async
    public void processPaymentAsync(Order order) {
        logger.info("Starting async payment processing - orderNumber: {}", order.getOrderNumber());

        try {
            // Update order status to PROCESSING
            order.markAsProcessing();
            orderRepository.save(order);

            // Process payment
            PaymentRequest paymentRequest = new PaymentRequest(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getSubtotal(),
                    order.getCustomerEmail()
            );

            PaymentResult result = paymentService.processPayment(paymentRequest);

            // Publish PaymentCompletedEvent
            paymentCompletedService.publishPaymentCompleted(order, result);

        } catch (PaymentException ex) {
            logger.error("Payment processing failed - orderNumber: {}, error: {}", 
                    order.getOrderNumber(), ex.getMessage(), ex);

            // Publish failed payment event
            PaymentResult failureResult = PaymentResult.failure(ex.getMessage());
            paymentCompletedService.publishPaymentCompleted(order, failureResult);
        }
    }
}

