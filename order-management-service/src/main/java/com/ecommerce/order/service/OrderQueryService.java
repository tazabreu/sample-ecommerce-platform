package com.ecommerce.order.service;

import com.ecommerce.order.dto.CancelOrderRequest;
import com.ecommerce.order.dto.FulfillOrderRequest;
import com.ecommerce.order.dto.OrderDto;
import com.ecommerce.order.dto.OrderPageDto;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.ResourceNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for querying and managing orders.
 * 
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Order lookups by order number (guest access)</li>
 *   <li>Order list with filtering and pagination (manager access)</li>
 *   <li>Order cancellation (manager access)</li>
 *   <li>Order fulfillment (manager access)</li>
 * </ul>
 * 
 * <p>Business Rules:</p>
 * <ul>
 *   <li>Orders can only be cancelled in PENDING or PROCESSING status</li>
 *   <li>Orders can only be fulfilled in PAID status</li>
 *   <li>Fulfillment records tracking number and carrier</li>
 * </ul>
 */
@Service
@Transactional
public class OrderQueryService {

    private static final Logger logger = LoggerFactory.getLogger(OrderQueryService.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderQueryService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    /**
     * Get order by order number (guest lookup).
     * Allows customers to track their orders without authentication.
     *
     * @param orderNumber the order number
     * @return the order
     * @throws ResourceNotFoundException if order not found
     */
    @Transactional(readOnly = true)
    public OrderDto getOrderByNumber(String orderNumber) {
        logger.debug("Looking up order by number: {}", orderNumber);
        
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));

        return orderMapper.toDto(order);
    }

    /**
     * List orders with filtering and pagination (manager access).
     *
     * @param status optional status filter
     * @param customerEmail optional email filter
     * @param startDate optional start date filter
     * @param endDate optional end date filter
     * @param pageable pagination parameters
     * @return page of orders
     */
    @Transactional(readOnly = true)
    public OrderPageDto listOrders(
            OrderStatus status,
            String customerEmail,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    ) {
        logger.debug("Listing orders - status: {}, email: {}, startDate: {}, endDate: {}, page: {}", 
                status, customerEmail, startDate, endDate, pageable.getPageNumber());

        Page<Order> orders = orderRepository.findWithFilters(
                customerEmail,
                status,
                startDate,
                endDate,
                pageable
        );

        return new OrderPageDto(
                orders.map(orderMapper::toDto).getContent(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages(),
                orders.isFirst(),
                orders.isLast()
        );
    }

    /**
     * Cancel an order (manager access).
     * Only allows cancellation for orders in PENDING or PROCESSING status.
     *
     * @param orderNumber the order number
     * @param request the cancellation request with reason
     * @return the cancelled order
     * @throws ResourceNotFoundException if order not found
     * @throws InvalidOrderStateException if order cannot be cancelled
     */
    public OrderDto cancelOrder(String orderNumber, CancelOrderRequest request) {
        logger.info("Cancelling order: {}, reason: {}", orderNumber, request.reason());

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));

        if (!order.canBeCancelled()) {
            throw new InvalidOrderStateException(
                    String.format("Order cannot be cancelled in status: %s. Order number: %s",
                            order.getStatus(), orderNumber)
            );
        }

        order.cancel();
        order = orderRepository.save(order);

        logger.info("Order cancelled successfully - orderNumber: {}, previousStatus: PENDING/PROCESSING", 
                orderNumber);

        return orderMapper.toDto(order);
    }

    /**
     * Mark an order as fulfilled (manager access).
     * Only allows fulfillment for orders in PAID status.
     *
     * @param orderNumber the order number
     * @param request the fulfillment request with tracking info
     * @return the fulfilled order
     * @throws ResourceNotFoundException if order not found
     * @throws InvalidOrderStateException if order cannot be fulfilled
     */
    public OrderDto fulfillOrder(String orderNumber, FulfillOrderRequest request) {
        logger.info("Fulfilling order: {}, trackingNumber: {}, carrier: {}", 
                orderNumber, request.trackingNumber(), request.carrier());

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));

        // Validate order can be fulfilled
        if (order.getStatus() != OrderStatus.PAID) {
            throw new InvalidOrderStateException(
                    String.format("Order can only be fulfilled in PAID status. Current status: %s, Order number: %s", 
                            order.getStatus(), orderNumber)
            );
        }

        order.markAsFulfilled();

        // Note: In a real system, you would store tracking info in a separate entity or in order metadata
        // For now, just log it
        logger.info("Order fulfilled - orderNumber: {}, trackingNumber: {}, carrier: {}, notes: {}", 
                orderNumber, request.trackingNumber(), request.carrier(), request.notes());

        order = orderRepository.save(order);

        logger.info("Order fulfilled successfully - orderNumber: {}", orderNumber);

        return orderMapper.toDto(order);
    }
}

