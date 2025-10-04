package com.ecommerce.order.service;

import com.ecommerce.shared.event.PaymentCompletedEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.model.PaymentTransaction;
import com.ecommerce.order.payment.PaymentResult;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for handling PaymentCompletedEvent.
 * 
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Publish PaymentCompletedEvent after payment processing</li>
 *   <li>Consume PaymentCompletedEvent to update order/payment status</li>
 *   <li>Handle idempotency for event processing</li>
 * </ul>
 * 
 * <p>Event Processing Flow:</p>
 * <ol>
 *   <li>Check idempotency (eventId in processed_events table)</li>
 *   <li>Update PaymentTransaction status and details</li>
 *   <li>Update Order status based on payment result</li>
 *   <li>Record eventId in processed_events table</li>
 *   <li>Acknowledge Kafka message</li>
 * </ol>
 */
@Service
public class PaymentCompletedService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCompletedService.class);

    @Value("${kafka.topics.payments-completed:payments.completed}")
    private String paymentCompletedTopic;

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;
    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    public PaymentCompletedService(
            KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate,
            OrderRepository orderRepository,
            ProcessedEventRepository processedEventRepository
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Publish PaymentCompletedEvent to Kafka.
     *
     * @param order the order
     * @param paymentResult the payment result
     */
    public void publishPaymentCompleted(Order order, PaymentResult paymentResult) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = "payment-" + order.getOrderNumber();
        }

        PaymentTransaction transaction = order.getPaymentTransaction();
        
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                correlationId,
                order.getId(),
                order.getOrderNumber(),
                transaction.getId(),
                paymentResult.success() ? "SUCCESS" : "FAILED",
                order.getSubtotal(),
                transaction.getPaymentMethod(),
                paymentResult.externalTransactionId(),
                paymentResult.failureReason()
        );

        logger.info("Publishing PaymentCompletedEvent - orderNumber: {}, status: {}", 
                order.getOrderNumber(), event.status());

        // Use orderId as partition key
        String partitionKey = order.getId().toString();

        kafkaTemplate.send(paymentCompletedTopic, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        logger.info("Successfully published PaymentCompletedEvent - orderNumber: {}, topic: {}, partition: {}, offset: {}", 
                                order.getOrderNumber(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        logger.error("Failed to publish PaymentCompletedEvent - orderNumber: {}, error: {}", 
                                order.getOrderNumber(), ex.getMessage(), ex);
                    }
                });
    }

    /**
     * Kafka listener for PaymentCompletedEvent.
     * Processes events from the payments.completed topic.
     *
     * @param event the payment completed event
     * @param partition the Kafka partition
     * @param offset the Kafka offset
     * @param ack the acknowledgment handle for manual commit
     */
    @KafkaListener(
            topics = "${kafka.topics.payments-completed:payments.completed}",
            groupId = "${spring.kafka.consumer.group-id:order-service-group}",
            containerFactory = "paymentCompletedEventListenerFactory"
    )
    public void onPaymentCompletedEvent(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        // Set correlation ID in MDC for tracing
        MDC.put("correlationId", event.correlationId());
        MDC.put("orderNumber", event.orderNumber());

        logger.info("Received PaymentCompletedEvent - orderNumber: {}, status: {}, partition: {}, offset: {}", 
                event.orderNumber(), event.status(), partition, offset);

        try {
            processPaymentCompletedEvent(event);
            
            // Acknowledge message after successful processing
            ack.acknowledge();
            
            logger.info("Successfully processed PaymentCompletedEvent - orderNumber: {}", 
                    event.orderNumber());
        } catch (Exception ex) {
            logger.error("Failed to process PaymentCompletedEvent - orderNumber: {}, error: {}", 
                    event.orderNumber(), ex.getMessage(), ex);
            
            // Don't acknowledge - message will be redelivered
            throw new RuntimeException("Payment completed event processing failed", ex);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Process PaymentCompletedEvent and update order/payment status.
     *
     * @param event the payment completed event
     */
    @Transactional
    public void processPaymentCompletedEvent(PaymentCompletedEvent event) {
        // 1. Check idempotency
        if (processedEventRepository.existsByEventId(event.eventId())) {
            logger.warn("Duplicate PaymentCompletedEvent detected - eventId: {}, orderNumber: {}. Skipping.", 
                    event.eventId(), event.orderNumber());
            return;
        }

        logger.info("Processing new PaymentCompletedEvent - orderNumber: {}, eventId: {}, status: {}", 
                event.orderNumber(), event.eventId(), event.status());

        // 2. Find order
        Order order = orderRepository.findByOrderNumber(event.orderNumber())
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found for PaymentCompletedEvent: " + event.orderNumber()
                ));

        PaymentTransaction transaction = order.getPaymentTransaction();
        if (transaction == null) {
            throw new IllegalStateException(
                    "PaymentTransaction not found for order: " + event.orderNumber()
            );
        }

        boolean isSuccess = "SUCCESS".equals(event.status());

        if (isSuccess) {
            transaction.markAsSuccessful(event.externalTransactionId());
            logger.info("Payment successful - orderNumber: {}, externalTransactionId: {}",
                    order.getOrderNumber(), event.externalTransactionId());
        } else {
            transaction.markAsFailed(event.failureReason() != null ? event.failureReason() : "Unknown failure");
            logger.warn("Payment failed - orderNumber: {}, reason: {}",
                    order.getOrderNumber(), event.failureReason());
        }

        // 4. Update Order status
        if (isSuccess) {
            order.markAsPaid();
            logger.info("Order status updated to PAID - orderNumber: {}", order.getOrderNumber());
        } else {
            order.markAsFailed();
            logger.warn("Order status updated to FAILED - orderNumber: {}", order.getOrderNumber());
        }

        orderRepository.save(order);

        // 5. Record event as processed
        com.ecommerce.order.model.ProcessedEvent processedEvent = 
                new com.ecommerce.order.model.ProcessedEvent(event.eventId(), event.eventType());
        processedEventRepository.save(processedEvent);

        logger.info("Recorded processed event - eventId: {}, orderNumber: {}", 
                event.eventId(), event.orderNumber());
    }
}

