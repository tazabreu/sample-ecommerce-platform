package com.ecommerce.customer.event;

import com.ecommerce.customer.model.OrderCreatedOutbox;
import com.ecommerce.customer.repository.OrderCreatedOutboxRepository;
import com.ecommerce.shared.event.OrderCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Background publisher for transactional outbox pattern.
 * Polls pending events from outbox and publishes them to Kafka.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Polls every 5 seconds for PENDING events</li>
 *   <li>Processes events in FIFO order (by creation time)</li>
 *   <li>Marks events as PUBLISHED on success</li>
 *   <li>Increments retry count on failure</li>
 *   <li>Moves to FAILED status after 5 retries</li>
 *   <li>Logs errors for monitoring/alerting</li>
 * </ul>
 */
@Component
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 5;
    private static final String TOPIC = "order-events";

    private final OrderCreatedOutboxRepository outboxRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(
            OrderCreatedOutboxRepository outboxRepository,
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Poll and publish pending outbox events.
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void publishPendingEvents() {
        List<OrderCreatedOutbox> pending = outboxRepository.findPendingEvents(BATCH_SIZE);

        if (pending.isEmpty()) {
            return;
        }

        logger.info("Publishing {} pending outbox events", pending.size());

        for (OrderCreatedOutbox outbox : pending) {
            try {
                publishEvent(outbox);
            } catch (Exception e) {
                handlePublishFailure(outbox, e);
            }
        }
    }

    /**
     * Publish single outbox event to Kafka.
     *
     * @param outbox the outbox event to publish
     * @throws Exception if publishing fails
     */
    @Transactional
    protected void publishEvent(OrderCreatedOutbox outbox) throws Exception {
        // Deserialize event
        OrderCreatedEvent event = objectMapper.readValue(outbox.getPayload(), OrderCreatedEvent.class);

        // Publish to Kafka
        kafkaTemplate.send(TOPIC, event.orderId().toString(), event).get();

        // Mark as published
        outbox.markAsPublished();
        outboxRepository.save(outbox);

        logger.info("Published outbox event - outboxId: {}, orderId: {}",
                outbox.getId(), outbox.getAggregateId());
    }

    /**
     * Handle publish failure with retry logic.
     *
     * @param outbox the failed outbox event
     * @param error the exception that occurred
     */
    @Transactional
    protected void handlePublishFailure(OrderCreatedOutbox outbox, Exception error) {
        outbox.incrementRetry();

        String errorMessage = error.getMessage();
        if (errorMessage != null && errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500);
        }

        if (outbox.getRetryCount() >= MAX_RETRIES) {
            outbox.markAsFailed(errorMessage);
            logger.error("Outbox event failed after {} retries - outboxId: {}, orderId: {}, error: {}",
                    MAX_RETRIES, outbox.getId(), outbox.getAggregateId(), error.getMessage());
        } else {
            outbox.setErrorMessage(errorMessage);
            logger.warn("Outbox event publish failed (retry {}/{}) - outboxId: {}, orderId: {}, error: {}",
                    outbox.getRetryCount(), MAX_RETRIES, outbox.getId(), outbox.getAggregateId(), error.getMessage());
        }

        outboxRepository.save(outbox);
    }
}
