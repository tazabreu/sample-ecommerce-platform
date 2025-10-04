package com.ecommerce.customer.service;

import com.ecommerce.customer.model.OrderNumberSequence;
import com.ecommerce.customer.repository.OrderNumberSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating unique order numbers using DB-backed sequences.
 *
 * <p>Order Number Format: ORD-YYYYMMDD-###</p>
 * <ul>
 *   <li>ORD: Prefix</li>
 *   <li>YYYYMMDD: Current date</li>
 *   <li>###: Sequential number (001-999, resets daily)</li>
 * </ul>
 *
 * <p>Concurrency Safety:</p>
 * <ul>
 *   <li>Uses SERIALIZABLE isolation to prevent phantom reads</li>
 *   <li>Pessimistic write locks on sequence rows</li>
 *   <li>Requires new transaction (REQUIRES_NEW) to ensure sequence increment is committed</li>
 * </ul>
 */
@Service
public class OrderNumberService {

    private static final Logger logger = LoggerFactory.getLogger(OrderNumberService.class);
    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ORDER_NUMBER_PREFIX = "ORD-";
    private static final int MAX_SEQUENCE_PER_DAY = 999;

    private final OrderNumberSequenceRepository sequenceRepository;

    public OrderNumberService(OrderNumberSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    /**
     * Generate next unique order number for today.
     *
     * <p>This method requires a new transaction to ensure the sequence increment
     * is committed immediately, preventing duplicate order numbers across concurrent requests.</p>
     *
     * @return generated order number in format ORD-YYYYMMDD-###
     * @throws IllegalStateException if daily sequence limit exceeded
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public String generateOrderNumber() {
        String dateKey = LocalDate.now().format(DATE_KEY_FORMAT);

        logger.debug("Generating order number for date: {}", dateKey);

        // Get or create sequence for today with pessimistic lock
        OrderNumberSequence sequence = sequenceRepository.findByDateKeyWithLock(dateKey)
                .orElseGet(() -> {
                    logger.info("Creating new order number sequence for date: {}", dateKey);
                    return new OrderNumberSequence(dateKey, 0);
                });

        // Increment sequence
        int nextSequence = sequence.getLastSequence() + 1;

        if (nextSequence > MAX_SEQUENCE_PER_DAY) {
            throw new IllegalStateException(
                    String.format("Daily order number limit exceeded for date %s (max: %d)",
                            dateKey, MAX_SEQUENCE_PER_DAY)
            );
        }

        sequence.setLastSequence(nextSequence);
        sequenceRepository.save(sequence);

        // Format order number: ORD-YYYYMMDD-###
        String orderNumber = String.format("%s%s-%03d", ORDER_NUMBER_PREFIX, dateKey, nextSequence);

        logger.info("Generated order number: {}", orderNumber);

        return orderNumber;
    }
}
