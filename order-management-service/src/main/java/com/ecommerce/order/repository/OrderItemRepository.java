package com.ecommerce.order.repository;

import com.ecommerce.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for OrderItem entity.
 * 
 * <p>Provides standard CRUD operations plus custom queries for order item analytics.</p>
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    /**
     * Finds all order items for a specific order.
     *
     * @param orderId the order ID
     * @return a list of order items
     */
    List<OrderItem> findByOrderId(UUID orderId);

    /**
     * Finds all order items for a specific product.
     * Useful for product sales analytics.
     *
     * @param productId the product ID
     * @return a list of order items
     */
    List<OrderItem> findByProductId(UUID productId);

    /**
     * Counts the number of items in a specific order.
     *
     * @param orderId the order ID
     * @return the count of order items
     */
    long countByOrderId(UUID orderId);

    /**
     * Calculates the total quantity sold for a specific product.
     * Useful for sales reporting.
     *
     * @param productId the product ID
     * @return the sum of quantities sold
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi WHERE oi.productId = :productId")
    Long sumQuantityByProductId(@Param("productId") UUID productId);

    /**
     * Finds the top N best-selling products by total quantity sold.
     * Useful for product popularity analytics.
     *
     * @param limit the maximum number of products to return
     * @return a list of product IDs ordered by total quantity sold (descending)
     */
    @Query("SELECT oi.productId, SUM(oi.quantity) as totalQuantity " +
           "FROM OrderItem oi " +
           "GROUP BY oi.productId " +
           "ORDER BY totalQuantity DESC")
    List<Object[]> findTopSellingProducts(@Param("limit") int limit);

    /**
     * Finds all order items for a specific product SKU.
     * Useful when you have SKU but not product ID.
     *
     * @param productSku the product SKU
     * @return a list of order items
     */
    List<OrderItem> findByProductSku(String productSku);
}

