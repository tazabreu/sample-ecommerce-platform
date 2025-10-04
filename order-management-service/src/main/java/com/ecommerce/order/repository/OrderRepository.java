package com.ecommerce.order.repository;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Order entity.
 * 
 * <p>Provides standard CRUD operations plus custom queries for order management,
 * filtering, and analytics.</p>
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Finds an order by its order number.
     *
     * @param orderNumber the unique order number (e.g., ORD-20250930-001)
     * @return an Optional containing the order, or empty if not found
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Checks if an order with the given order number exists.
     *
     * @param orderNumber the order number
     * @return true if an order exists, false otherwise
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * Finds all orders for a specific customer email.
     *
     * @param customerEmail the customer email
     * @param pageable      pagination information
     * @return a page of orders
     */
    Page<Order> findByCustomerEmail(String customerEmail, Pageable pageable);

    /**
     * Finds all orders with a specific status.
     *
     * @param status   the order status
     * @param pageable pagination information
     * @return a page of orders
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Finds all orders created between two timestamps.
     *
     * @param startDate the start timestamp (inclusive)
     * @param endDate   the end timestamp (inclusive)
     * @param pageable  pagination information
     * @return a page of orders
     */
    Page<Order> findByCreatedAtBetween(Instant startDate, Instant endDate, Pageable pageable);

    /**
     * Finds all orders with a specific status created between two timestamps.
     *
     * @param status    the order status
     * @param startDate the start timestamp (inclusive)
     * @param endDate   the end timestamp (inclusive)
     * @param pageable  pagination information
     * @return a page of orders
     */
    Page<Order> findByStatusAndCreatedAtBetween(OrderStatus status, Instant startDate, 
                                                  Instant endDate, Pageable pageable);

    /**
     * Finds all orders for a customer with a specific status.
     *
     * @param customerEmail the customer email
     * @param status        the order status
     * @param pageable      pagination information
     * @return a page of orders
     */
    Page<Order> findByCustomerEmailAndStatus(String customerEmail, OrderStatus status, Pageable pageable);

    /**
     * Finds orders using complex filtering criteria.
     * This method demonstrates JPQL query construction for multiple optional filters.
     *
     * @param customerEmail optional customer email filter (null to ignore)
     * @param status        optional order status filter (null to ignore)
     * @param startDate     optional start date filter (null to ignore)
     * @param endDate       optional end date filter (null to ignore)
     * @param pageable      pagination information
     * @return a page of orders matching the filters
     */
    @Query("SELECT o FROM Order o WHERE " +
           "(:customerEmail IS NULL OR o.customerEmail = :customerEmail) AND " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:startDate IS NULL OR o.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR o.createdAt <= :endDate)")
    Page<Order> findWithFilters(@Param("customerEmail") String customerEmail,
                                 @Param("status") OrderStatus status,
                                 @Param("startDate") Instant startDate,
                                 @Param("endDate") Instant endDate,
                                 Pageable pageable);

    /**
     * Counts orders by status.
     *
     * @param status the order status
     * @return the count of orders
     */
    long countByStatus(OrderStatus status);

    /**
     * Finds all orders that are cancellable (status PENDING or PROCESSING).
     *
     * @return a list of cancellable orders
     */
    @Query("SELECT o FROM Order o WHERE o.status IN ('PENDING', 'PROCESSING')")
    List<Order> findCancellableOrders();

    /**
     * Finds all orders that are ready for fulfillment (status PAID).
     *
     * @return a list of orders ready for fulfillment
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'PAID'")
    List<Order> findOrdersReadyForFulfillment();

    /**
     * Finds the most recent orders (for dashboard/analytics).
     *
     * @param limit the maximum number of orders to return
     * @return a list of recent orders
     */
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> findRecentOrders(Pageable pageable);
}

