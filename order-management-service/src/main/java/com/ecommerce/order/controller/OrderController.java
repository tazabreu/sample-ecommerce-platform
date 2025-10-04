package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CancelOrderRequest;
import com.ecommerce.order.dto.FulfillOrderRequest;
import com.ecommerce.order.dto.OrderDto;
import com.ecommerce.order.dto.OrderPageDto;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.service.OrderQueryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for order management operations.
 * 
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/v1/orders/{orderNumber} - Get order by order number (public for guest lookup)</li>
 *   <li>GET /api/v1/orders - List orders with filters (manager only)</li>
 *   <li>POST /api/v1/orders/{orderNumber}/cancel - Cancel order (manager only)</li>
 *   <li>POST /api/v1/orders/{orderNumber}/fulfill - Mark order as fulfilled (manager only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderQueryService orderQueryService;

    public OrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    /**
     * Get order by order number.
     * Public endpoint - allows guests to look up their order status.
     *
     * @param orderNumber the order number (format: ORD-YYYYMMDD-NNN)
     * @return the order details
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderDto> getOrderByNumber(@PathVariable String orderNumber) {
        logger.info("Getting order by number: {}", orderNumber);
        OrderDto order = orderQueryService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(order);
    }

    /**
     * List orders with optional filters and pagination.
     * Manager role required.
     *
     * @param status optional order status filter
     * @param customerEmail optional customer email filter
     * @param startDate optional start date filter (ISO 8601 format)
     * @param endDate optional end date filter (ISO 8601 format)
     * @param page page number (0-indexed)
     * @param size page size
     * @param sortBy field to sort by (default: createdAt)
     * @param sortDir sort direction (asc or desc)
     * @return paginated list of orders
     */
    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<OrderPageDto> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        logger.info("Listing orders - status: {}, email: {}, startDate: {}, endDate: {}, page: {}, size: {}", 
                status, customerEmail, startDate, endDate, page, size);

        // Create pageable with sorting
        Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? Sort.by(sortBy).descending() 
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // Get orders
        OrderPageDto ordersPage = orderQueryService.listOrders(
                status, customerEmail, startDate, endDate, pageable
        );

        logger.info("Found {} orders (page {} of {})",
                ordersPage.orders().size(), ordersPage.currentPage() + 1, ordersPage.totalPages());

        return ResponseEntity.ok(ordersPage);
    }

    /**
     * Cancel an order.
     * Manager role required.
     * Only orders with status PENDING or PROCESSING can be cancelled.
     *
     * @param orderNumber the order number
     * @param request the cancel request with reason
     * @return the updated order
     */
    @PostMapping("/{orderNumber}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<OrderDto> cancelOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        logger.info("Cancelling order - orderNumber: {}, reason: {}", orderNumber, request.reason());
        OrderDto order = orderQueryService.cancelOrder(orderNumber, request);
        logger.info("Order cancelled successfully - orderNumber: {}, newStatus: {}", 
                orderNumber, order.status());
        return ResponseEntity.ok(order);
    }

    /**
     * Mark order as fulfilled (shipped).
     * Manager role required.
     * Only orders with status PAID can be fulfilled.
     *
     * @param orderNumber the order number
     * @param request the fulfill request with tracking information
     * @return the updated order
     */
    @PostMapping("/{orderNumber}/fulfill")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<OrderDto> fulfillOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody FulfillOrderRequest request
    ) {
        logger.info("Fulfilling order - orderNumber: {}, trackingNumber: {}", 
                orderNumber, request.trackingNumber());
        
        OrderDto order = orderQueryService.fulfillOrder(orderNumber, request);
        
        logger.info("Order fulfilled successfully - orderNumber: {}, newStatus: {}", 
                orderNumber, order.status());
        
        return ResponseEntity.ok(order);
    }
}

