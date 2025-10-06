package com.ecommerce.order.repository;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
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
public interface OrderRepository extends PagingAndSortingRepository<Order, UUID> {

    /**
     * Find order by ID.
     */
    Optional<Order> findById(UUID id);

    /**
     * Persist order explicitly (required in JDBC).
     */
    <S extends Order> S save(S entity);

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
     * This method demonstrates SQL query construction for multiple optional filters.
     *
     * Note: Returns List instead of Page because Spring Data JDBC doesn't support
     * Page return types with @Query string-based queries. Use Pageable for LIMIT/OFFSET.
     *
     * PostgreSQL Enum Handling: Accepts String for status parameter to avoid JDBC type inference
     * issues with nullable enum parameters. The service layer converts OrderStatus enum to String.
     * We compare the enum column as text for clean nullable parameter handling.
     *
     * @param customerEmail optional customer email filter (null to ignore)
     * @param statusString  optional order status filter as String (null to ignore)
     * @param startDate     optional start date filter (null to ignore)
     * @param endDate       optional end date filter (null to ignore)
     * @param limit         maximum number of results
     * @param offset        number of results to skip
     * @return a list of orders matching the filters
     */
    @Query("SELECT * FROM orders WHERE " +
           "(COALESCE(:customerEmail, customer_email) = customer_email) AND " +
           "(COALESCE(:statusString, status::text) = status::text) AND " +
           "(created_at >= COALESCE(:startDate, created_at)) AND " +
           "(created_at <= COALESCE(:endDate, created_at)) " +
           "ORDER BY created_at DESC " +
           "LIMIT :limit OFFSET :offset")
    List<Order> findWithFilters(@Param("customerEmail") String customerEmail,
                                 @Param("statusString") String statusString,
                                 @Param("startDate") Instant startDate,
                                 @Param("endDate") Instant endDate,
                                 @Param("limit") int limit,
                                 @Param("offset") long offset);

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
    @Query("SELECT * FROM orders WHERE status IN ('PENDING', 'PROCESSING')")
    List<Order> findCancellableOrders();

    /**
     * Finds all orders that are ready for fulfillment (status PAID).
     *
     * @return a list of orders ready for fulfillment
     */
    @Query("SELECT * FROM orders WHERE status = 'PAID'")
    List<Order> findOrdersReadyForFulfillment();

    /**
     * Finds the most recent orders (for dashboard/analytics).
     *
     * Note: Returns List instead of Page because Spring Data JDBC doesn't support
     * Page return types with @Query string-based queries.
     *
     * @param limit the maximum number of orders to return
     * @return a list of recent orders
     */
    @Query("SELECT * FROM orders ORDER BY created_at DESC LIMIT :limit")
    List<Order> findRecentOrders(@Param("limit") int limit);
}
