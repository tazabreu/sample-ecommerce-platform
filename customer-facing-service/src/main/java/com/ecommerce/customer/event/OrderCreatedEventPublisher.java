package com.ecommerce.customer.event;

import com.ecommerce.shared.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publisher for OrderCreatedEvent to Kafka.
 * Handles event publishing with proper error handling and correlation ID propagation.
 */
@Component
public class OrderCreatedEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreatedEventPublisher.class);

    @Value("${kafka.topics.orders-created:orders.created}")
    private String orderCreatedTopic;

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderCreatedEventPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish an OrderCreatedEvent to Kafka.
     * Uses orderId as partition key to ensure ordering for the same order.
     *
     * @param event the event to publish
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, OrderCreatedEvent>> publishOrderCreated(OrderCreatedEvent event) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = event.correlationId();
        }

        logger.info("Publishing OrderCreatedEvent - orderNumber: {}, correlationId: {}", 
                event.orderNumber(), correlationId);

        // Use orderId as partition key for ordering guarantees
        String partitionKey = event.orderId().toString();

        CompletableFuture<SendResult<String, OrderCreatedEvent>> future = 
                kafkaTemplate.send(orderCreatedTopic, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published OrderCreatedEvent - orderNumber: {}, topic: {}, partition: {}, offset: {}", 
                        event.orderNumber(), 
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish OrderCreatedEvent - orderNumber: {}, error: {}", 
                        event.orderNumber(), ex.getMessage(), ex);
            }
        });

        return future;
    }
}


