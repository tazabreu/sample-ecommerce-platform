package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.OrderNumberSequence;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for order number sequence operations.
 * Provides pessimistic locking for concurrent-safe sequence generation.
 */
@Repository
public interface OrderNumberSequenceRepository extends CrudRepository<OrderNumberSequence, String> {

    /**
     * Find order number sequence by date key with pessimistic write lock.
     * Ensures only one transaction can increment the sequence at a time.
     *
     * @param dateKey the date key in YYYYMMDD format
     * @return the order number sequence, if found
     */
    @Query("SELECT * FROM order_number_sequence WHERE date_key = :dateKey FOR UPDATE")
    Optional<OrderNumberSequence> findByDateKeyWithLock(@Param("dateKey") String dateKey);
}
