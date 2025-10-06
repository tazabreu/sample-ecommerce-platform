package com.ecommerce.order.repository;

import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.model.PaymentTransaction;
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
 * Repository interface for PaymentTransaction entity.
 * 
 * <p>Provides standard CRUD operations plus custom queries for payment transaction
 * management, analytics, and reconciliation.</p>
 */
@Repository
public interface PaymentTransactionRepository extends PagingAndSortingRepository<PaymentTransaction, UUID> {

    /**
     * Find payment transaction by ID.
     */
    Optional<PaymentTransaction> findById(UUID id);

    /**
     * Persist payment transaction explicitly (required in JDBC).
     */
    <S extends PaymentTransaction> S save(S entity);

    /**
     * Finds a payment transaction by order ID.
     *
     * @param orderId the order ID
     * @return an Optional containing the payment transaction, or empty if not found
     */
    Optional<PaymentTransaction> findByOrderId(UUID orderId);

    /**
     * Finds a payment transaction by external transaction ID.
     * Useful for webhook processing and reconciliation with payment providers.
     *
     * @param externalTransactionId the external payment provider transaction ID
     * @return an Optional containing the payment transaction, or empty if not found
     */
    Optional<PaymentTransaction> findByExternalTransactionId(String externalTransactionId);

    /**
     * Finds all payment transactions with a specific status.
     *
     * @param status   the payment status
     * @param pageable pagination information
     * @return a page of payment transactions
     */
    Page<PaymentTransaction> findByStatus(PaymentStatus status, Pageable pageable);

    /**
     * Finds all payment transactions with a specific status created between two timestamps.
     * Useful for payment reporting and reconciliation.
     *
     * @param status    the payment status
     * @param startDate the start timestamp (inclusive)
     * @param endDate   the end timestamp (inclusive)
     * @param pageable  pagination information
     * @return a page of payment transactions
     */
    Page<PaymentTransaction> findByStatusAndCreatedAtBetween(PaymentStatus status, 
                                                               Instant startDate, 
                                                               Instant endDate, 
                                                               Pageable pageable);

    /**
     * Finds all payment transactions for a specific payment method.
     *
     * @param paymentMethod the payment method (e.g., MOCK, STRIPE, PAYPAL)
     * @return a list of payment transactions
     */
    List<PaymentTransaction> findByPaymentMethod(String paymentMethod);

    /**
     * Counts payment transactions by status.
     *
     * @param status the payment status
     * @return the count of payment transactions
     */
    long countByStatus(PaymentStatus status);

    /**
     * Finds all pending payment transactions older than a specific time.
     * Useful for identifying stuck/hanging payments that need manual intervention.
     *
     * @param createdBefore the timestamp threshold
     * @return a list of pending payment transactions
     */
    @Query("SELECT * FROM payment_transactions WHERE status = 'PENDING' AND created_at < :createdBefore")
    List<PaymentTransaction> findStalePendingPayments(@Param("createdBefore") Instant createdBefore);

    /**
     * Calculates the payment success rate (percentage of successful payments).
     *
     * @return the success rate as a decimal (0.0 to 1.0), or null if no payments
     */
    @Query("SELECT CASE WHEN COUNT(*) = 0 THEN NULL ELSE " +
           "CAST(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS double precision) / " +
           "CAST(COUNT(*) AS double precision) END " +
           "FROM payment_transactions")
    Double calculateSuccessRate();

    /**
     * Finds all payment transactions that have been retried multiple times.
     * Useful for identifying problematic payment scenarios.
     *
     * @param minAttempts the minimum number of attempts
     * @return a list of payment transactions with retry attempts >= minAttempts
     */
    @Query("SELECT * FROM payment_transactions WHERE attempt_count >= :minAttempts")
    List<PaymentTransaction> findTransactionsWithMultipleAttempts(@Param("minAttempts") int minAttempts);

    /**
     * Finds all failed payment transactions created between two timestamps.
     * Useful for failure analysis and reporting.
     *
     * @param startDate the start timestamp (inclusive)
     * @param endDate   the end timestamp (inclusive)
     * @return a list of failed payment transactions
     */
    @Query("SELECT * FROM payment_transactions WHERE status = 'FAILED' AND created_at BETWEEN :startDate AND :endDate")
    List<PaymentTransaction> findFailedPaymentsBetween(@Param("startDate") Instant startDate, 
                                                        @Param("endDate") Instant endDate);
}
